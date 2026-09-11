package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceAuthorizeRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceResponse;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.checkin.security.ScannerDeviceCredentialService;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScannerDeviceServiceImpl implements ScannerDeviceService {

    private final ScannerDeviceRepository scannerDeviceRepository;
    private final CheckInConfigRepository checkInConfigRepository;
    private final EventRepository eventRepository;
    private final ScannerDeviceCredentialService credentialService;
    private final OrganizationAccessGuard accessGuard;

    @Override
    @Transactional
    public ScannerDeviceResponse authorize(UUID eventId, ScannerDeviceAuthorizeRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        // Code-reviewer HIGH: locked (not a plain findByEventId) so two
        // concurrent authorize() calls for a pure_offline event actually
        // serialize on this row, rather than both reading "no active device
        // yet" under READ_COMMITTED and both inserting one - see
        // CheckInConfigRepository.findByEventIdForUpdate's javadoc.
        CheckInMode mode = checkInConfigRepository.findByEventIdForUpdate(eventId).map(CheckInConfig::getMode).orElse(CheckInMode.STANDARD);
        if (mode == CheckInMode.PURE_OFFLINE) {
            List<ScannerDevice> activeDevices = scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE);
            if (!activeDevices.isEmpty()) {
                if (!request.forceReplaceOrDefault()) {
                    throw new ConflictException("pure_offline mode already has an active device; retry with force_replace true");
                }
                // BR-CHECKIN-008: pure_offline has exactly one authorized
                // device - the override flow revokes whatever's currently
                // active before authorizing the replacement.
                String callerId = accessGuard.currentUserId().toString();
                for (ScannerDevice existing : activeDevices) {
                    existing.setStatus(ScannerDeviceStatus.REVOKED);
                    existing.setUpdatedBy(callerId);
                    scannerDeviceRepository.save(existing);
                }
            }
        }

        UUID deviceId = UUID.randomUUID();
        String credential = credentialService.generate(deviceId);
        ScannerDevice device = ScannerDevice.builder()
                .id(deviceId)
                .eventId(eventId)
                .deviceLabel(request.deviceLabel())
                .status(ScannerDeviceStatus.ACTIVE)
                .createdBy(accessGuard.currentUserId().toString())
                .build();
        ScannerDevice saved = scannerDeviceRepository.save(device);
        return ScannerDeviceResponse.from(saved, credential);
    }

    @Override
    public void revoke(UUID deviceId) {
        ScannerDevice device = scannerDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Scanner device " + deviceId + " not found"));
        Event event = getEventOrThrow(device.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        device.setStatus(ScannerDeviceStatus.REVOKED);
        device.setUpdatedBy(accessGuard.currentUserId().toString());
        scannerDeviceRepository.save(device);
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
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's scanner devices");
        }
    }
}
