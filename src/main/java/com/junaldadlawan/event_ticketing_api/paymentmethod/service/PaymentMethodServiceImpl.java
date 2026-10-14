package com.junaldadlawan.event_ticketing_api.paymentmethod.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodRequest;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodResponse;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.SetupIntentResponse;
import com.junaldadlawan.event_ticketing_api.paymentmethod.entity.PaymentMethod;
import com.junaldadlawan.event_ticketing_api.paymentmethod.gateway.StripeGateway;
import com.junaldadlawan.event_ticketing_api.paymentmethod.repository.PaymentMethodRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private static final String FAILING_TOKEN_PREFIX = "tok_fail";

    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;
    private final StripeCustomerService stripeCustomerService;
    private final StripeGateway stripeGateway;
    private final OrganizationAccessGuard accessGuard;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list() {
        return paymentMethodRepository.findByUserIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(accessGuard.currentUserId())
                .stream().map(PaymentMethodResponse::from).toList();
    }

    @Override
    public SetupIntentResponse createSetupIntent() {
        if (!stripeGateway.live()) {
            return new SetupIntentResponse(stripeGateway.createSetupIntent(null));
        }
        String customerId = stripeCustomerService.getOrCreate(accessGuard.currentUserId());
        return new SetupIntentResponse(stripeGateway.createSetupIntent(customerId));
    }

    @Override
    @Transactional
    public PaymentMethodResponse add(PaymentMethodRequest request) {
        UUID userId = accessGuard.currentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));

        String stripePaymentMethodId = null;
        String brand;
        String last4;
        int expMonth;
        int expYear;
        if (stripeGateway.live()) {
            if (!request.paymentToken().startsWith("pm_")) {
                throw new BadRequestException("paymentToken must be a Stripe payment method id (pm_...)");
            }
            if (paymentMethodRepository.existsByUserIdAndStripePaymentMethodIdAndDeletedAtIsNull(userId, request.paymentToken())) {
                throw new ConflictException("That card is already saved");
            }
            // The card's details come from Stripe; anything the client sent for them is ignored.
            StripeGateway.CardDetails card = stripeGateway.attachAndDescribe(stripeCustomerService.getOrCreate(userId), request.paymentToken());
            stripePaymentMethodId = request.paymentToken();
            brand = card.brand();
            last4 = card.last4();
            expMonth = card.expMonth();
            expYear = card.expYear();
        } else {
            if (request.paymentToken().startsWith(FAILING_TOKEN_PREFIX)) {
                throw new BadRequestException("The card was declined");
            }
            if (!request.paymentToken().startsWith("tok_")) {
                throw new BadRequestException("paymentToken must look like tok_<brand> with the mock gateway");
            }
            if (request.last4() == null || request.expMonth() == null || request.expYear() == null) {
                throw new BadRequestException("last4, expMonth and expYear are required with the mock gateway");
            }
            brand = request.brand() != null ? request.brand() : request.paymentToken().substring("tok_".length());
            last4 = request.last4();
            expMonth = request.expMonth();
            expYear = request.expYear();
        }
        if (YearMonth.of(expYear, expMonth).isBefore(YearMonth.now(ZoneOffset.UTC))) {
            throw new BadRequestException("The card has expired");
        }

        boolean first = paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId) == 0;
        boolean primary = first || Boolean.TRUE.equals(request.primary());
        if (primary && !first) {
            demoteCurrentPrimary(userId);
        }
        PaymentMethod saved = paymentMethodRepository.save(PaymentMethod.builder()
                .userId(userId)
                .stripePaymentMethodId(stripePaymentMethodId)
                .holderName(request.holderName() != null ? request.holderName().trim() : user.fullName())
                .brand(brand.trim())
                .last4(last4)
                .expMonth(expMonth)
                .expYear(expYear)
                .primary(primary)
                .build());
        // Only the last 4 are logged, never a token.
        BusinessAuditLogger.record("payment_method.added", "PaymentMethod", saved.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "brand=" + saved.getBrand() + " last4=" + saved.getLast4() + " primary=" + primary);
        return PaymentMethodResponse.from(saved);
    }

    @Override
    @Transactional
    public PaymentMethodResponse setPrimary(UUID id) {
        PaymentMethod card = getOwnOrThrow(id);
        if (!card.isPrimary()) {
            demoteCurrentPrimary(card.getUserId());
            card.setPrimary(true);
            card = paymentMethodRepository.save(card);
            BusinessAuditLogger.record("payment_method.primary_set", "PaymentMethod", card.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                    "last4=" + card.getLast4());
        }
        return PaymentMethodResponse.from(card);
    }

    @Override
    @Transactional
    public void remove(UUID id) {
        PaymentMethod card = getOwnOrThrow(id);
        if (card.isPrimary() && paymentMethodRepository.countByUserIdAndDeletedAtIsNull(card.getUserId()) > 1) {
            throw new ConflictException("Make another card primary before removing the primary card");
        }
        if (stripeGateway.live() && card.getStripePaymentMethodId() != null) {
            stripeGateway.detach(card.getStripePaymentMethodId());
        }
        card.setPrimary(false);
        card.markDeleted();
        paymentMethodRepository.save(card);
        BusinessAuditLogger.record("payment_method.removed", "PaymentMethod", card.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "last4=" + card.getLast4());
    }

    @Override
    @Transactional
    public SavedCardCharge resolveForCheckout(UUID id) {
        PaymentMethod card = getOwnOrThrow(id);
        if (stripeGateway.live() && card.getStripePaymentMethodId() != null) {
            return new SavedCardCharge(card.getStripePaymentMethodId(), stripeCustomerService.getOrCreate(card.getUserId()));
        }
        return new SavedCardCharge("tok_" + card.getBrand().toLowerCase().replace(' ', '_'), null);
    }

    /** Unsets the old primary and flushes first, so the one-primary unique index never sees two. */
    private void demoteCurrentPrimary(UUID userId) {
        paymentMethodRepository.findByUserIdAndPrimaryTrueAndDeletedAtIsNull(userId).ifPresent(old -> {
            old.setPrimary(false);
            paymentMethodRepository.saveAndFlush(old);
        });
    }

    /** The caller's own card, or a 404: another user's card is never revealed. */
    private PaymentMethod getOwnOrThrow(UUID id) {
        return paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(id, accessGuard.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method " + id + " not found"));
    }
}
