package com.junaldadlawan.event_ticketing_api.refundpolicy.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyResponse;
import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyUpdateRequest;
import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import com.junaldadlawan.event_ticketing_api.refundpolicy.repository.RefundPolicyRepository;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundPolicyServiceImpl implements RefundPolicyService {

    private final RefundPolicyRepository refundPolicyRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public RefundPolicyResponse get(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        requireOrganizerAdminOrBuyerWithOrder(event);
        return refundPolicyRepository.findByEventId(eventId)
                .map(RefundPolicyResponse::from)
                .orElseGet(() -> RefundPolicyResponse.defaultFor(eventId));
    }

    @Override
    public RefundPolicyResponse update(UUID eventId, RefundPolicyUpdateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        var existing = refundPolicyRepository.findByEventId(eventId);
        RefundPolicy policy = existing.orElseGet(() -> RefundPolicy.builder().eventId(eventId).build());
        applyRequest(policy, request);

        if (existing.isPresent()) {
            return RefundPolicyResponse.from(refundPolicyRepository.save(policy));
        }
        try {
            return RefundPolicyResponse.from(refundPolicyRepository.saveAndFlush(policy));
        } catch (DataIntegrityViolationException e) {
            // Lost a race to a concurrent first-time PATCH for the same
            // event - V16's unique index on event_id is the backstop, same
            // idiom as ResalePolicyServiceImpl.update.
            RefundPolicy winner = refundPolicyRepository.findByEventId(eventId)
                    .orElseThrow(() -> e);
            applyRequest(winner, request);
            return RefundPolicyResponse.from(refundPolicyRepository.save(winner));
        }
    }

    private void applyRequest(RefundPolicy policy, RefundPolicyUpdateRequest request) {
        policy.setRuleType(request.ruleType());
        policy.setDaysBeforeEvent(request.daysBeforeEvent());
        policy.setCustomTerms(request.customTerms());
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * Mirrors {@code ResalePolicyServiceImpl}/{@code TicketTemplateServiceImpl}'s
     * copy of the same BR-AUTH-004 admin-bypass shape.
     */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's refund policy");
        }
    }

    /**
     * openapi.yaml's getRefundPolicy summary: "organizer, admin, or a buyer
     * with an order on it" - wider than {@link #requireOwnerOrOrganizerOrAdmin},
     * so not reused; a buyer needs to know the terms before requesting a
     * refund, unlike {@code ResalePolicy}'s fully-public GET.
     */
    private void requireOrganizerAdminOrBuyerWithOrder(Event event) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.ORGANIZER);
        if (isOwnerOrOrganizer) {
            return;
        }
        if (!ticketRepository.existsByEventIdAndOwnerId(event.getId(), callerId)) {
            throw new ForbiddenException("Only the event's organizer/owner, an admin, or a buyer with an order on this event may view its refund policy");
        }
    }
}
