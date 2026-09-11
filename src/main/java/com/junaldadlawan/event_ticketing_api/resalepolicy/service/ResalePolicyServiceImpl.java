package com.junaldadlawan.event_ticketing_api.resalepolicy.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyResponse;
import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyUpdateRequest;
import com.junaldadlawan.event_ticketing_api.resalepolicy.entity.ResalePolicy;
import com.junaldadlawan.event_ticketing_api.resalepolicy.repository.ResalePolicyRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResalePolicyServiceImpl implements ResalePolicyService {

    private final ResalePolicyRepository resalePolicyRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public ResalePolicyResponse get(UUID eventId) {
        getEventOrThrow(eventId);
        return resalePolicyRepository.findByEventId(eventId)
                .map(ResalePolicyResponse::from)
                .orElseGet(() -> ResalePolicyResponse.defaultFor(eventId));
    }

    @Override
    public ResalePolicyResponse update(UUID eventId, ResalePolicyUpdateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        var existing = resalePolicyRepository.findByEventId(eventId);
        ResalePolicy policy = existing.orElseGet(() -> ResalePolicy.builder().eventId(eventId).build());
        applyRequest(policy, request);

        if (existing.isPresent()) {
            return ResalePolicyResponse.from(resalePolicyRepository.save(policy));
        }
        try {
            return ResalePolicyResponse.from(resalePolicyRepository.saveAndFlush(policy));
        } catch (DataIntegrityViolationException e) {
            // Lost a race to a concurrent first-time PATCH for the same
            // event - V14's unique index on event_id is the backstop, same
            // idiom as ResaleListingServiceImpl.create's active-listing race.
            ResalePolicy winner = resalePolicyRepository.findByEventId(eventId)
                    .orElseThrow(() -> e);
            applyRequest(winner, request);
            return ResalePolicyResponse.from(resalePolicyRepository.save(winner));
        }
    }

    private void applyRequest(ResalePolicy policy, ResalePolicyUpdateRequest request) {
        policy.setEnabled(request.enabled());
        policy.setPriceCapRule(request.priceCapRule());
        MoneyDto feeAmount = request.feeAmount();
        policy.setFeeAmount(feeAmount != null ? Money.builder().amount(feeAmount.amount()).currency(feeAmount.currency()).build() : null);
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * Mirrors {@code TicketTemplateServiceImpl}/{@code TicketTypeServiceImpl}'s
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
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's resale policy");
        }
    }
}
