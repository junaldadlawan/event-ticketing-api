package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigUpdateRequest;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInConfigServiceImpl implements CheckInConfigService {

    private final CheckInConfigRepository checkInConfigRepository;
    private final ScannerDeviceRepository scannerDeviceRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public CheckInConfigResponse get(UUID eventId) {
        getEventOrThrow(eventId);
        return checkInConfigRepository.findByEventId(eventId)
                .map(CheckInConfigResponse::from)
                .orElseGet(() -> CheckInConfigResponse.defaultFor(eventId));
    }

    @Override
    public CheckInConfigResponse update(UUID eventId, CheckInConfigUpdateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        var existing = checkInConfigRepository.findByEventId(eventId);
        CheckInMode previousMode = existing.map(CheckInConfig::getMode).orElse(CheckInMode.STANDARD);
        CheckInConfig config = existing.orElseGet(() -> CheckInConfig.builder().eventId(eventId).mode(CheckInMode.STANDARD).build());
        applyRequest(config, request);

        String warning = computeModeSwitchWarning(eventId, previousMode, config.getMode());

        if (existing.isPresent()) {
            return CheckInConfigResponse.from(checkInConfigRepository.save(config), warning);
        }
        try {
            return CheckInConfigResponse.from(checkInConfigRepository.saveAndFlush(config), warning);
        } catch (DataIntegrityViolationException e) {
            // Lost a race to a concurrent first-time PATCH for the same
            // event - V18's unique index on event_id is the backstop, same
            // idiom as ResalePolicyServiceImpl/RefundPolicyServiceImpl.update.
            CheckInConfig winner = checkInConfigRepository.findByEventId(eventId).orElseThrow(() -> e);
            applyRequest(winner, request);
            return CheckInConfigResponse.from(checkInConfigRepository.save(winner), warning);
        }
    }

    private void applyRequest(CheckInConfig config, CheckInConfigUpdateRequest request) {
        if (request.mode() != null) {
            config.setMode(request.mode());
        }
        if (request.offlineFallbackExpirySeconds() != null) {
            config.setOfflineFallbackExpirySeconds(request.offlineFallbackExpirySeconds());
        }
        config.setUpdatedBy(accessGuard.currentUserId().toString());
    }

    /**
     * Best-effort approximation, not a real sync-state check (confirmed
     * decision: no scanner device in this dispatch reports its live sync
     * status back to the server - see {@code DeviceAuthenticationFilter}'s
     * javadoc). A warning is surfaced whenever the mode is actually
     * changing AND at least one ACTIVE device exists for the event, since
     * that device may be holding scans it hasn't reported back yet.
     */
    private String computeModeSwitchWarning(UUID eventId, CheckInMode previousMode, CheckInMode newMode) {
        if (previousMode == newMode) {
            return null;
        }
        boolean hasActiveDevice = scannerDeviceRepository.countByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE) > 0;
        return hasActiveDevice
                ? "Switching check-in modes while a device may hold unsynced local scan data - ensure all devices have fully synced before relying on the new mode."
                : null;
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's check-in config");
        }
    }
}
