package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.analytics.service.FinancePeriod;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.payout.dto.EligiblePayoutsResponse;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutBatchRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutGenerateRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutWalletResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutAccount;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutAccountRepository;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutEligibilityRepository;
import com.junaldadlawan.event_ticketing_api.payout.security.AccountNumberCipher;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import com.junaldadlawan.event_ticketing_api.payout.enums.EventScope;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutRepository;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRepository payoutRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final PayoutCardRepository payoutCardRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final PayoutTransferClient transferClient;
    private final AccountNumberCipher cipher;
    private final PlatformTransactionManager transactionManager;
    private final EventRepository eventRepository;
    private final PayoutEligibilityRepository eligibilityRepository;
    private final AuditLogService auditLogService;

    @Override
    public Page<PayoutResponse> list(UUID organizationId, Pageable pageable) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization " + organizationId + " not found");
        }
        requireOwnerOrOrganizerOrAdmin(organizationId);
        Page<Payout> page = payoutRepository.findByOrganizationId(organizationId, pageable);
        Set<UUID> eventIds = new HashSet<>();
        page.forEach(payout -> {
            if (payout.getEventId() != null) {
                eventIds.add(payout.getEventId());
            }
        });
        Map<UUID, String> titles = new HashMap<>();
        if (!eventIds.isEmpty()) {
            eventRepository.findAllById(eventIds).forEach(event -> titles.put(event.getId(), event.getTitle()));
        }
        return page.map(payout -> PayoutResponse.from(payout, payout.getEventId() == null ? null : titles.get(payout.getEventId())));
    }

    @Override
    @Transactional
    public PayoutResponse generate(UUID organizationId, PayoutGenerateRequest request) {
        accessGuard.requireAdmin();
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization " + organizationId + " not found");
        }
        LocalDate start = request.periodStart();
        LocalDate end = request.periodEnd();
        if (end.isBefore(start)) {
            throw new BadRequestException("periodEnd must not be before periodStart");
        }
        if (end.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new BadRequestException("periodEnd must not be in the future");
        }

        Destination destination = destination(organizationId);
        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Order> orders = orderRepository.findDueForPayout(organizationId, from, to);
        if (orders.isEmpty()) {
            throw new ConflictException("No orders are due for payout in that period");
        }
        return PayoutResponse.from(createPayout(organizationId, start, end, orders, null, destination,
                "The orders in that period use more than one currency; pay them out in separate periods"));
    }

    @Override
    @Transactional(readOnly = true)
    public EligiblePayoutsResponse eligible(LocalDate periodStart, LocalDate periodEnd, UUID organizationId, EventScope scope) {
        accessGuard.requireAdmin();
        FinancePeriod period = period(periodStart, periodEnd);
        if (organizationId != null && !organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization " + organizationId + " not found");
        }
        List<PayoutEligibilityRepository.Line> lines = eligibilityRepository.lines(period.fromInstant(), period.toInstant(), organizationId,
                scope == null ? EventScope.COMPLETED : scope);
        String currency = lines.isEmpty() ? "PHP" : lines.get(0).currency();
        List<EligiblePayoutsResponse.Item> items = new java.util.ArrayList<>();
        long ready = 0;
        long onHold = 0;
        Set<UUID> readyOrganizations = new HashSet<>();
        for (PayoutEligibilityRepository.Line line : lines) {
            String hold = line.pendingRefund() ? "Pending refunds" : line.currencies() > 1 ? "More than one currency" : null;
            items.add(new EligiblePayoutsResponse.Item(line.organizationId(), line.organizationName(), line.eventId(), line.eventTitle(),
                    new MoneyDto(line.eligible(), line.currency()),
                    hold == null ? EligiblePayoutsResponse.Status.READY : EligiblePayoutsResponse.Status.ON_HOLD, hold));
            // the summary is in the currency of the first line; a line mixing currencies has no single amount to add
            if (!line.currency().equals(currency) || line.currencies() > 1) {
                continue;
            }
            if (hold == null) {
                ready += line.eligible();
                readyOrganizations.add(line.organizationId());
            } else {
                onHold += line.eligible();
            }
        }
        return new EligiblePayoutsResponse(new EligiblePayoutsResponse.Summary(readyOrganizations.size(), new MoneyDto(ready, currency),
                new MoneyDto(onHold, currency)), items);
    }

    @Override
    @Transactional
    public List<PayoutResponse> generateBatch(PayoutBatchRequest request) {
        accessGuard.requireAdmin();
        FinancePeriod period = period(request.periodStart(), request.periodEnd());
        Set<String> seen = new HashSet<>();
        for (PayoutBatchRequest.Line line : request.items()) {
            if (!seen.add(line.organizationId() + ":" + line.eventId())) {
                throw new BadRequestException("The same organization and event appear twice in the list");
            }
        }
        List<PayoutResponse> created = new java.util.ArrayList<>();
        for (PayoutBatchRequest.Line line : request.items()) {
            Organization organization = organizationRepository.findById(line.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization " + line.organizationId() + " not found"));
            Event event = eventRepository.findByIdAndDeletedAtIsNull(line.eventId())
                    .filter(found -> organization.getId().equals(found.getOrganizationId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Event " + line.eventId() + " not found for organization " + organization.getName()));
            String label = organization.getName() + " / " + event.getTitle();
            if (organization.getStatus() != OrganizationStatus.APPROVED) {
                throw new ConflictException(label + ": the organization is not approved");
            }
            Destination destination = destination(organization.getId());
            List<Order> orders = orderRepository.findDueForPayoutByEvent(organization.getId(), event.getId(), period.fromInstant(), period.toInstant());
            if (orders.isEmpty()) {
                throw new ConflictException("Nothing is due for payout for " + label + " in that period");
            }
            if (refundRepository.existsByOrderIdInAndStatus(orders.stream().map(Order::getId).toList(), RefundStatus.PENDING)) {
                throw new ConflictException(label + " is on hold: a refund is still pending");
            }
            created.add(PayoutResponse.from(createPayout(organization.getId(), period.from(), period.to(), orders, event.getId(), destination,
                    label + ": the orders use more than one currency"), event.getTitle()));
        }
        return created;
    }

    /** Both days given, the period sensible (see {@link FinancePeriod}) and the last day not in the future. */
    private static FinancePeriod period(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw new BadRequestException("periodStart and periodEnd are required");
        }
        if (periodEnd.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new BadRequestException("periodEnd must not be in the future");
        }
        return FinancePeriod.resolve(periodStart, periodEnd);
    }

    /** Where a payout goes: PayMongo pays into a bank or e-wallet account; the other modes keep paying out to the primary card. */
    private record Destination(PayoutCard card, PayoutAccount account) {
    }

    private Destination destination(UUID organizationId) {
        if (transferClient.requiresAccount()) {
            return new Destination(null, payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId)
                    .orElseThrow(() -> new ConflictException("The organization has no payout account; add a bank or e-wallet account before generating a payout")));
        }
        return new Destination(payoutCardRepository.findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(organizationId)
                .orElseThrow(() -> new ConflictException("The organization has no primary payout card; add one before generating a payout")), null);
    }

    /** Settles the orders into one SCHEDULED payout (of one event when {@code eventId} is set) and marks them as settled. */
    private Payout createPayout(UUID organizationId, LocalDate start, LocalDate end, List<Order> orders, UUID eventId, Destination destination,
                                String mixedCurrencyMessage) {
        String currency = orders.get(0).getTotal().getCurrency();
        if (orders.stream().anyMatch(order -> !currency.equals(order.getTotal().getCurrency()))) {
            throw new ConflictException(mixedCurrencyMessage);
        }

        long gross = 0;
        long fees = 0;
        for (Order order : orders) {
            long orderGross = Math.max(0, order.getTotal().getAmount() - refundRepository.sumCompletedAmountByOrderId(order.getId()));
            // The platform fee is kept in full, unless refunds have already eaten into it (when the platform bears it, it is waived and
            // nothing comes off). So is the payment gateway's fee when the customer (it is part of what they paid) or the organization
            // bears it; when the platform absorbs it, nothing comes off.
            long orderGatewayFee = order.gatewayFeeDeduction();
            long orderPlatformFee = order.getPlatformFeePayer() == com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.PLATFORM ? 0L : order.getPlatformFeeAmount();
            long orderFee = Math.min(orderPlatformFee + orderGatewayFee, orderGross);
            gross += orderGross;
            fees += orderFee;
        }

        Payout payout = payoutRepository.save(Payout.builder()
                .organizationId(organizationId)
                .eventId(eventId)
                .gross(money(gross, currency))
                .fees(money(fees, currency))
                .net(money(gross - fees, currency))
                .periodStart(start)
                .periodEnd(end)
                .status(PayoutStatus.SCHEDULED)
                .payoutCardId(destination.card() == null ? null : destination.card().getId())
                .payoutAccountId(destination.account() == null ? null : destination.account().getId())
                .build());
        for (Order order : orders) {
            order.setPayoutId(payout.getId());
        }
        orderRepository.saveAll(orders);

        BusinessAuditLogger.record("payout.generated", "Payout", payout.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + organizationId + (eventId == null ? "" : " event=" + eventId) + " orders=" + orders.size() + " gross=" + gross
                        + " fees=" + fees + " net=" + (gross - fees) + " " + currency);
        return payout;
    }

    @Override
    public PayoutResponse send(UUID payoutId) {
        accessGuard.requireAdmin();
        // Step 1 (own transaction, committed before the money call): claim the payout, so a second send or a crash can never send twice.
        PayoutTransferClient.TransferRequest request = inTransaction().execute(status -> {
            Payout payout = payoutRepository.findByIdForUpdate(payoutId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payout " + payoutId + " not found"));
            if (payout.getStatus() != PayoutStatus.SCHEDULED && payout.getStatus() != PayoutStatus.FAILED) {
                throw new ConflictException("Only a scheduled or failed payout can be sent; this one is " + payout.getStatus());
            }
            if (payout.getNet().getAmount() <= 0) {
                throw new ConflictException("The payout has nothing to send");
            }
            String bankCode = null;
            String accountName = null;
            String accountNumber = null;
            if (transferClient.requiresAccount()) {
                PayoutAccount account = payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(payout.getOrganizationId())
                        .orElseThrow(() -> new ConflictException("The organization has no payout account; add one before sending the payout"));
                payout.setPayoutAccountId(account.getId());
                bankCode = account.getBankCode();
                accountName = account.getAccountName();
                accountNumber = cipher.decrypt(account.getAccountNumberEncrypted());
            }
            payout.setStatus(PayoutStatus.PROCESSING);
            payout.setTransferAttempts(payout.getTransferAttempts() + 1);
            payout.setFailureReason(null);
            payoutRepository.save(payout);
            // PayMongo wants a new reference number for every retry
            String reference = payout.getId() + (payout.getTransferAttempts() > 1 ? "-" + payout.getTransferAttempts() : "");
            return new PayoutTransferClient.TransferRequest(reference, payout.getNet().getAmount(), payout.getNet().getCurrency(), bankCode,
                    accountName, accountNumber, "Event ticket payout " + payout.getPeriodStart() + " to " + payout.getPeriodEnd(),
                    java.util.Map.of("payout_id", payout.getId().toString()));
        });

        PayoutTransferClient.TransferResult result;
        try {
            result = transferClient.send(request);
        } catch (RuntimeException e) {
            // The provider may or may not have the transfer, so the reason says to check there first; the payout can then be sent again.
            settle(payoutId, new PayoutTransferClient.TransferResult(null, PayoutTransferClient.Status.FAILED, e instanceof ApiException
                    ? "Could not confirm the transfer with the payment provider. Check the provider dashboard before sending again"
                    : "The transfer failed unexpectedly. Check the payment provider dashboard before sending again"));
            throw e;
        }
        return settle(payoutId, result);
    }

    @Override
    @Transactional
    public PayoutResponse cancel(UUID payoutId) {
        accessGuard.requireAdmin();
        if (payoutRepository.cancelIfScheduled(payoutId, Instant.now()) == 0) {
            Payout payout = payoutRepository.findById(payoutId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payout " + payoutId + " not found"));
            throw new ConflictException("Only a scheduled payout can be cancelled; this one is " + payout.getStatus());
        }
        int released = orderRepository.releaseFromPayout(payoutId);
        Payout payout = payoutRepository.findById(payoutId).orElseThrow();
        auditLogService.record(accessGuard.currentUserId(), "payout.cancelled", "Payout", payoutId);
        BusinessAuditLogger.record("payout.cancelled", "Payout", payoutId, BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + payout.getOrganizationId() + " orders=" + released + " net=" + payout.getNet().getAmount() + " " + payout.getNet().getCurrency());
        return PayoutResponse.from(payout);
    }

    @Override
    public PayoutWalletResponse wallet() {
        accessGuard.requireAdmin();
        return PayoutWalletResponse.from(transferClient.wallet());
    }

    @Override
    public PayoutResponse refresh(UUID payoutId) {
        accessGuard.requireAdmin();
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout " + payoutId + " not found"));
        if (payout.getStatus() != PayoutStatus.PROCESSING || payout.getTransferId() == null) {
            throw new ConflictException("Only a payout whose transfer is being processed can be refreshed; this one is " + payout.getStatus());
        }
        return settle(payoutId, transferClient.retrieve(payout.getTransferId()));
    }

    @Override
    public void onTransferResult(String transferId, boolean succeeded, String failureReason) {
        inTransaction().executeWithoutResult(status -> payoutRepository.findByTransferIdForUpdate(transferId).ifPresent(payout ->
                apply(payout, new PayoutTransferClient.TransferResult(transferId,
                        succeeded ? PayoutTransferClient.Status.SUCCEEDED : PayoutTransferClient.Status.FAILED, failureReason))));
    }

    /** Step 2: records what the provider answered (in its own transaction). Does nothing to a payout that is no longer PROCESSING. */
    private PayoutResponse settle(UUID payoutId, PayoutTransferClient.TransferResult result) {
        return inTransaction().execute(status -> {
            Payout payout = payoutRepository.findByIdForUpdate(payoutId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payout " + payoutId + " not found"));
            apply(payout, result);
            return PayoutResponse.from(payout);
        });
    }

    private void apply(Payout payout, PayoutTransferClient.TransferResult result) {
        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            return;
        }
        if (result.transferId() != null) {
            payout.setTransferId(result.transferId());
        }
        switch (result.status()) {
            case SUCCEEDED -> {
                payout.setStatus(PayoutStatus.PAID);
                payout.setFailureReason(null);
            }
            case FAILED -> {
                payout.setStatus(PayoutStatus.FAILED);
                String reason = result.failureReason() == null ? "The transfer failed" : result.failureReason();
                payout.setFailureReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
            }
            case PENDING -> { }
        }
        payoutRepository.save(payout);
        BusinessAuditLogger.record("payout." + result.status().name().toLowerCase(), "Payout", payout.getId(),
                result.status() == PayoutTransferClient.Status.FAILED ? BusinessAuditLogger.Outcome.FAILURE : BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + payout.getOrganizationId() + " transfer=" + payout.getTransferId() + " attempt=" + payout.getTransferAttempts()
                        + " net=" + payout.getNet().getAmount() + " " + payout.getNet().getCurrency()
                        + (payout.getFailureReason() == null ? "" : " reason=" + payout.getFailureReason()));
    }

    private TransactionTemplate inTransaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static Money money(long amount, String currency) {
        return Money.builder().amount(amount).currency(currency).build();
    }

    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may view its payouts");
        }
    }
}
