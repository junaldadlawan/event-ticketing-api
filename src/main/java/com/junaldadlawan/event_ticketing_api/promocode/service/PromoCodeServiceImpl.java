package com.junaldadlawan.event_ticketing_api.promocode.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.enums.PromoCodeStatus;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromoCodeServiceImpl implements PromoCodeService {

    private static final String ALREADY_USED_CHANGE =
            "This code has already been used; pause it and create a new one instead";

    private final PromoCodeRepository promoCodeRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrderRepository orderRepository;

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
        requireValidDiscount(request.discountType(), request.discountValue());

        requireCodeFree(eventId, request.code(), null);

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
        return saveOrConflict(promoCode);
    }

    @Override
    public List<PromoCode> list(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        // Owning organizer/admin only, always — unlike TicketType's list,
        // openapi.yaml's listPromoCodes has no `security: []` override and
        // no public/draft-based visibility split, confirmed against the
        // actual spec text ("owning organizer" only). Paused codes are listed
        // too; only soft-deleted ones are not.
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        return promoCodeRepository.findByEventIdAndDeletedAtIsNull(eventId);
    }

    @Override
    public PromoCode update(UUID promoCodeId, PromoCodeUpdateRequest request) {
        PromoCode promoCode = getOrThrow(promoCodeId);
        // The owning organization always comes from the promo code's own event, never from client input.
        Event event = getEventOrThrow(promoCode.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        int used = usedCount(promoCode);

        // Work out every new value first and validate the merged result; only then touch the entity.
        String newCode = promoCode.getCode();
        if (request.code() != null) {
            if (request.code().isBlank()) {
                throw new BadRequestException("Promo code must not be blank");
            }
            newCode = request.code();
        }
        DiscountType newType = request.discountType() != null ? request.discountType() : promoCode.getDiscountType();
        BigDecimal newValue = request.discountValue() != null ? request.discountValue() : promoCode.getDiscountValue();
        Instant newFrom = request.validFrom() != null ? request.validFrom() : promoCode.getValidFrom();
        Instant newUntil = request.validUntil() != null ? request.validUntil() : promoCode.getValidUntil();

        boolean codeChanges = !newCode.equals(promoCode.getCode());
        boolean typeChanges = newType != promoCode.getDiscountType();
        if (used > 0 && (codeChanges || typeChanges)) {
            throw new ConflictException(ALREADY_USED_CHANGE);
        }
        if (codeChanges) {
            requireCodeFree(promoCode.getEventId(), newCode, promoCodeId);
        }
        requireValidDiscount(newType, newValue);
        if (!newUntil.isAfter(newFrom)) {
            throw new BadRequestException("validUntil must be after validFrom");
        }

        Set<UUID> newTicketTypeIds = null;
        if (request.applicableTicketTypeIds() != null) {
            newTicketTypeIds = new HashSet<>(request.applicableTicketTypeIds());
            requireTicketTypesOfEvent(newTicketTypeIds, promoCode.getEventId());
        }
        // 0 means "no limit" (stored as null); a total below what has already been used makes no sense.
        Integer newLimitTotal = promoCode.getUsageLimitTotal();
        if (request.usageLimitTotal() != null) {
            newLimitTotal = request.usageLimitTotal() == 0 ? null : request.usageLimitTotal();
            if (newLimitTotal != null && newLimitTotal < used) {
                throw new BadRequestException("usageLimitTotal can't be below the " + used + " times this code has already been used");
            }
        }
        Integer newLimitPerBuyer = promoCode.getUsageLimitPerBuyer();
        if (request.usageLimitPerBuyer() != null) {
            newLimitPerBuyer = request.usageLimitPerBuyer() == 0 ? null : request.usageLimitPerBuyer();
        }

        promoCode.setCode(newCode);
        promoCode.setDiscountType(newType);
        promoCode.setDiscountValue(newValue);
        promoCode.setValidFrom(newFrom);
        promoCode.setValidUntil(newUntil);
        promoCode.setUsageLimitTotal(newLimitTotal);
        promoCode.setUsageLimitPerBuyer(newLimitPerBuyer);
        if (newTicketTypeIds != null) {
            promoCode.getApplicableTicketTypeIds().clear();
            promoCode.getApplicableTicketTypeIds().addAll(newTicketTypeIds);
        }
        PromoCode saved = saveOrConflict(promoCode);
        BusinessAuditLogger.record("promo_code.updated", "PromoCode", promoCodeId, BusinessAuditLogger.Outcome.SUCCESS);
        return saved;
    }

    @Override
    public PromoCode setStatus(UUID promoCodeId, PromoCodeStatus status) {
        PromoCode promoCode = getOrThrow(promoCodeId);
        Event event = getEventOrThrow(promoCode.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        boolean paused = status == PromoCodeStatus.PAUSED;
        if (promoCode.isPaused() == paused) {
            return promoCode;
        }
        promoCode.setPaused(paused);
        PromoCode saved = promoCodeRepository.save(promoCode);
        BusinessAuditLogger.record(paused ? "promo_code.paused" : "promo_code.resumed", "PromoCode", promoCodeId,
                BusinessAuditLogger.Outcome.SUCCESS);
        return saved;
    }

    @Override
    public void delete(UUID promoCodeId) {
        PromoCode promoCode = getOrThrow(promoCodeId);
        Event event = getEventOrThrow(promoCode.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        // Orders keep only the code string, but a used code is part of the sales record: pause it instead.
        if (usedCount(promoCode) > 0) {
            throw new ConflictException("This code has already been used, so it can't be deleted. Pause it instead.");
        }
        promoCode.markDeleted();
        promoCodeRepository.save(promoCode);
        BusinessAuditLogger.record("promo_code.deleted", "PromoCode", promoCodeId, BusinessAuditLogger.Outcome.SUCCESS);
    }

    @Override
    public int usedCount(PromoCode promoCode) {
        return Math.toIntExact(orderRepository.countPromoCodeUses(promoCode.getCode(), promoCode.getEventId()));
    }

    private PromoCode getOrThrow(UUID promoCodeId) {
        return promoCodeRepository.findByIdAndDeletedAtIsNull(promoCodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Promo code " + promoCodeId + " not found"));
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    private static void requireValidDiscount(DiscountType type, BigDecimal value) {
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("discountValue must be <= 100 for a PERCENTAGE promo code");
        }
    }

    /** The code must not already be used by another (non-deleted) promo code of the same event. */
    private void requireCodeFree(UUID eventId, String code, UUID ownId) {
        promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, code)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("A promo code with code '" + code + "' already exists for this event");
                });
    }

    private void requireTicketTypesOfEvent(Set<UUID> ticketTypeIds, UUID eventId) {
        for (UUID ticketTypeId : ticketTypeIds) {
            boolean ofThisEvent = ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)
                    .map(TicketType::getEventId)
                    .filter(eventId::equals)
                    .isPresent();
            if (!ofThisEvent) {
                throw new BadRequestException("Ticket type " + ticketTypeId + " does not belong to this event");
            }
        }
    }

    /** A concurrent request taking the same code between our check and the insert must be a 409, not a 500. */
    private PromoCode saveOrConflict(PromoCode promoCode) {
        try {
            return promoCodeRepository.save(promoCode);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A promo code with code '" + promoCode.getCode() + "' already exists for this event");
        }
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
