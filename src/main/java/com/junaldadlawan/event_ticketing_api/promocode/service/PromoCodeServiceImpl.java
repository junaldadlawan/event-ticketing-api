package com.junaldadlawan.event_ticketing_api.promocode.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromoCodeServiceImpl implements PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public PromoCode create(UUID eventId, PromoCodeCreateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (!request.validUntil().isAfter(request.validFrom())) {
            throw new BadRequestException("validUntil must be after validFrom");
        }

        // openapi.yaml documents discount_value as "a percentage (0-100) if
        // discount_type is percentage"; @PositiveOrZero on the DTO already
        // covers the floor, this covers the upper bound.
        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("discountValue must be <= 100 for a PERCENTAGE promo code");
        }

        promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, request.code())
                .ifPresent(existing -> {
                    throw new ConflictException("A promo code with code '" + request.code() + "' already exists for this event");
                });

        Set<UUID> applicableTicketTypeIds = request.applicableTicketTypeIds() != null
                ? new HashSet<>(request.applicableTicketTypeIds())
                : new HashSet<>();

        PromoCode promoCode = PromoCode.builder()
                .eventId(event.getId())
                .code(request.code())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .applicableTicketTypeIds(applicableTicketTypeIds)
                .usageLimitTotal(request.usageLimitTotal())
                .usageLimitPerBuyer(request.usageLimitPerBuyer())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .build();
        return promoCodeRepository.save(promoCode);
    }

    @Override
    public List<PromoCode> list(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        // Owning organizer/admin only, always — unlike TicketType's list,
        // openapi.yaml's listPromoCodes has no `security: []` override and
        // no public/draft-based visibility split, confirmed against the
        // actual spec text ("owning organizer" only).
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        return promoCodeRepository.findByEventIdAndDeletedAtIsNull(eventId);
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * Mirrors {@code EventServiceImpl}/{@code TicketTypeServiceImpl}'s copy
     * of the same BR-AUTH-004 admin-bypass shape.
     */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's promo codes");
        }
    }
}
