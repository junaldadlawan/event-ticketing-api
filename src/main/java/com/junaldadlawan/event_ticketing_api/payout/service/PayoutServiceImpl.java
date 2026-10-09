package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutGenerateRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutRepository;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRepository payoutRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;

    @Override
    public Page<PayoutResponse> list(UUID organizationId, Pageable pageable) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization " + organizationId + " not found");
        }
        requireOwnerOrOrganizerOrAdmin(organizationId);
        return payoutRepository.findByOrganizationId(organizationId, pageable).map(PayoutResponse::from);
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

        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Order> orders = orderRepository.findDueForPayout(organizationId, from, to);
        if (orders.isEmpty()) {
            throw new ConflictException("No orders are due for payout in that period");
        }
        String currency = orders.get(0).getTotal().getCurrency();
        if (orders.stream().anyMatch(order -> !currency.equals(order.getTotal().getCurrency()))) {
            throw new ConflictException("The orders in that period use more than one currency; pay them out in separate periods");
        }

        long gross = 0;
        long fees = 0;
        for (Order order : orders) {
            long orderGross = Math.max(0, order.getTotal().getAmount() - refundRepository.sumCompletedAmountByOrderId(order.getId()));
            // The platform fee is kept in full, unless refunds have already eaten into it.
            long orderFee = Math.min(order.getPlatformFeeAmount(), orderGross);
            gross += orderGross;
            fees += orderFee;
        }

        Payout payout = payoutRepository.save(Payout.builder()
                .organizationId(organizationId)
                .gross(money(gross, currency))
                .fees(money(fees, currency))
                .net(money(gross - fees, currency))
                .periodStart(start)
                .periodEnd(end)
                .status(PayoutStatus.SCHEDULED)
                .build());
        for (Order order : orders) {
            order.setPayoutId(payout.getId());
        }
        orderRepository.saveAll(orders);

        BusinessAuditLogger.record("payout.generated", "Payout", payout.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + organizationId + " orders=" + orders.size() + " gross=" + gross + " fees=" + fees
                        + " net=" + (gross - fees) + " " + currency);
        return PayoutResponse.from(payout);
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
