package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PayoutCardServiceImpl implements PayoutCardService {

    private static final String DEMO_BRAND = "Visa";
    private static final String DEMO_LAST4 = "4242";
    private static final int DEMO_EXP_MONTH = 12;
    private static final int DEMO_EXP_YEAR = 2099;
    private static final String DEMO_TOKEN = "tok_demo_payout_card";

    private final PayoutCardRepository payoutCardRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;
    /** Mock payout gateway with the demo card on: every added card is saved as the same fixed demo card. */
    private final boolean demoCard;

    public PayoutCardServiceImpl(PayoutCardRepository payoutCardRepository, OrganizationRepository organizationRepository,
                                 OrganizationAccessGuard accessGuard,
                                 AuditLogService auditLogService,
                                 @Value("${payments.payout-gateway:mock}") String payoutGateway,
                                 @Value("${payments.demo-payout-card:true}") boolean demoPayoutCard) {
        this.payoutCardRepository = payoutCardRepository;
        this.organizationRepository = organizationRepository;
        this.accessGuard = accessGuard;
        this.auditLogService = auditLogService;
        this.demoCard = demoPayoutCard && !"paymongo".equals(payoutGateway);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutCardResponse> list(UUID organizationId) {
        requireOrganization(organizationId);
        if (!accessGuard.isAdmin()) {
            UUID callerId = accessGuard.currentUserId();
            if (!accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                    && !accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER)) {
                throw new ForbiddenException("Only the organization's owner, organizer, or an admin may view its payout cards");
            }
        }
        return payoutCardRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(organizationId).stream()
                .map(PayoutCardResponse::from).toList();
    }

    @Override
    @Transactional
    public PayoutCardResponse add(UUID organizationId, PayoutCardRequest request) {
        Organization organization = requireOrganization(organizationId);
        requireOwnerOrAdmin(organizationId, "add a payout card");
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ForbiddenException("Organization is not approved");
        }
        if (!demoCard && YearMonth.of(request.expYear(), request.expMonth()).isBefore(YearMonth.now(ZoneOffset.UTC))) {
            throw new BadRequestException("The card has expired");
        }

        boolean first = payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(organizationId) == 0;
        boolean primary = first || Boolean.TRUE.equals(request.primary());
        if (primary && !first) {
            demoteCurrentPrimary(organizationId);
        }
        PayoutCard saved = payoutCardRepository.save(PayoutCard.builder()
                .organizationId(organizationId)
                .holderName(request.holderName().trim())
                .brand(demoCard ? DEMO_BRAND : request.brand().trim())
                .last4(demoCard ? DEMO_LAST4 : request.last4())
                .expMonth(demoCard ? DEMO_EXP_MONTH : request.expMonth())
                .expYear(demoCard ? DEMO_EXP_YEAR : request.expYear())
                .paymentToken(demoCard ? DEMO_TOKEN : request.paymentToken())
                .primary(primary)
                .build());
        // Only the last 4 are logged, never the token.
        auditLogService.recordByCurrentUser("payout_card.added", "PayoutCard", saved.getId(), null, organizationId, card(saved));
        return PayoutCardResponse.from(saved);
    }

    @Override
    @Transactional
    public PayoutCardResponse setPrimary(UUID cardId) {
        PayoutCard card = getOrThrow(cardId);
        requireOwnerOrAdmin(card.getOrganizationId(), "change the primary payout card");
        if (!card.isPrimary()) {
            demoteCurrentPrimary(card.getOrganizationId());
            card.setPrimary(true);
            card = payoutCardRepository.save(card);
            auditLogService.recordByCurrentUser("payout_card.primary_set", "PayoutCard", card.getId(), null, card.getOrganizationId(), card(card));
        }
        return PayoutCardResponse.from(card);
    }

    @Override
    @Transactional
    public void remove(UUID cardId) {
        PayoutCard card = getOrThrow(cardId);
        requireOwnerOrAdmin(card.getOrganizationId(), "remove a payout card");
        if (card.isPrimary() && payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(card.getOrganizationId()) > 1) {
            throw new ConflictException("Make another card primary before removing the primary card");
        }
        card.setPrimary(false);
        card.markDeleted();
        payoutCardRepository.save(card);
        auditLogService.recordByCurrentUser("payout_card.removed", "PayoutCard", card.getId(), null, card.getOrganizationId(), card(card));
    }

    /** Brand and last 4 only - never the card number or token. */
    private static Map<String, Object> card(PayoutCard card) {
        Map<String, Object> details = AuditDetails.named(card.getBrand() + " ending " + card.getLast4());
        details.put("last4", card.getLast4());
        return details;
    }

    /** Unsets the old primary and flushes first, so the one-primary unique index never sees two. */
    private void demoteCurrentPrimary(UUID organizationId) {
        payoutCardRepository.findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(organizationId).ifPresent(old -> {
            old.setPrimary(false);
            payoutCardRepository.saveAndFlush(old);
        });
    }

    private PayoutCard getOrThrow(UUID cardId) {
        return payoutCardRepository.findByIdAndDeletedAtIsNull(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout card " + cardId + " not found"));
    }

    private Organization requireOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + organizationId + " not found"));
    }

    private void requireOwnerOrAdmin(UUID organizationId, String action) {
        if (accessGuard.isAdmin()) {
            return;
        }
        if (!accessGuard.hasRole(accessGuard.currentUserId(), organizationId, OrganizationRole.OWNER)) {
            throw new ForbiddenException("Only the organization's owner or an admin may " + action);
        }
    }
}
