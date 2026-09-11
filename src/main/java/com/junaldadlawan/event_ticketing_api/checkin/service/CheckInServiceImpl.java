package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInRecordResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.FallbackScanBatchRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.FallbackScanItemDto;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetEntryDto;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketSummaryDto;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidateScanRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidationResultResponse;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.FallbackScanRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.FallbackScanRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAccessGuard;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialService;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 10. {@link #validate} and each scan in {@link #submitFallbackScans}
 * both funnel through {@link #resolveAndMarkTicket} so the credential-
 * verification/status-transition mechanics (BR-CHECKIN-001/002/003) are
 * never duplicated between the two entry points.
 */
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final ScannerDeviceRepository scannerDeviceRepository;
    private final CheckInConfigRepository checkInConfigRepository;
    private final CheckInRecordRepository checkInRecordRepository;
    private final FallbackScanRecordRepository fallbackScanRecordRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final TicketCredentialService ticketCredentialService;
    private final DeviceAccessGuard deviceAccessGuard;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public TicketDatasetResponse getDataset(UUID deviceId) {
        // Same cross-check as validate(): the path's deviceId must match
        // the AUTHENTICATED device, or any active device could fetch any
        // OTHER device's (and therefore any other event's) full ticket
        // dataset just by changing the path variable.
        UUID authenticatedDeviceId = deviceAccessGuard.currentDeviceId();
        if (!authenticatedDeviceId.equals(deviceId)) {
            throw new ForbiddenException("This dataset belongs to a different device");
        }
        ScannerDevice device = requireOwnActiveDevice(deviceId);
        List<TicketDatasetEntryDto> entries = ticketRepository.findByEventId(device.getEventId()).stream()
                .map(t -> new TicketDatasetEntryDto(t.getId(), sha256(t.getCredential()), t.getStatus().name().toLowerCase(Locale.ROOT)))
                .toList();
        return new TicketDatasetResponse(device.getEventId(), Instant.now(), entries);
    }

    @Override
    @Transactional
    public ValidationResultResponse validate(ValidateScanRequest request) {
        // The body's device_id must match the AUTHENTICATED device
        // (DeviceAccessGuard.currentDeviceId(), from the verified bearer
        // credential) - trusting request.deviceId() on its own would let
        // any active device's credential submit a scan attributed to a
        // DIFFERENT device, bypassing that other device's own event scope.
        UUID authenticatedDeviceId = deviceAccessGuard.currentDeviceId();
        if (!authenticatedDeviceId.equals(request.deviceId())) {
            throw new ForbiddenException("device_id does not match the authenticated device");
        }
        ScannerDevice device = requireOwnActiveDevice(authenticatedDeviceId);

        ScanOutcome outcome = resolveAndMarkTicket(request.credential(), device.getEventId());
        Instant scannedAt = Instant.now();
        if (outcome.ticketId() != null) {
            checkInRecordRepository.save(CheckInRecord.builder()
                    .ticketId(outcome.ticketId())
                    .sourceType(CheckInSourceType.SCANNER_DEVICE)
                    .sourceId(device.getId())
                    .result(outcome.result())
                    .build());
        }
        return new ValidationResultResponse(outcome.ticketId(), outcome.result(), scannedAt, buildSummary(outcome.ticket()));
    }

    @Override
    @Transactional
    public List<ValidationResultResponse> submitFallbackScans(FallbackScanBatchRequest request) {
        ScannerDevice device = requireOwnActiveDevice(deviceAccessGuard.currentDeviceId());
        if (!device.getEventId().equals(request.eventId())) {
            throw new BadRequestException("event_id does not match the authenticated device's event");
        }
        CheckInMode mode = checkInConfigRepository.findByEventId(request.eventId())
                .map(CheckInConfig::getMode).orElse(CheckInMode.STANDARD);
        if (mode != CheckInMode.PURE_OFFLINE) {
            throw new ConflictException("Fallback scans are only accepted for events in pure_offline mode");
        }

        return request.scans().stream().map(scan -> reconcileFallbackScan(request.eventId(), device, scan)).toList();
    }

    private ValidationResultResponse reconcileFallbackScan(UUID eventId, ScannerDevice device, FallbackScanItemDto scan) {
        ScanOutcome outcome = resolveAndMarkTicket(scan.rawCredential(), eventId);

        FallbackScanRecord record = FallbackScanRecord.builder()
                .eventId(eventId)
                .rawCredential(scan.rawCredential())
                .capturedAt(scan.capturedAt())
                .syncedAt(Instant.now())
                .reconciledTicketId(outcome.ticketId())
                .build();
        FallbackScanRecord savedRecord = fallbackScanRecordRepository.save(record);

        if (outcome.ticketId() != null) {
            checkInRecordRepository.save(CheckInRecord.builder()
                    .ticketId(outcome.ticketId())
                    .sourceType(CheckInSourceType.RECONCILED_FALLBACK)
                    .sourceId(savedRecord.getId())
                    .result(outcome.result())
                    .build());
        }
        return new ValidationResultResponse(outcome.ticketId(), outcome.result(), savedRecord.getSyncedAt(), buildSummary(outcome.ticket()));
    }

    @Override
    public List<CheckInRecordResponse> listTicketCheckInRecords(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
        Event event = eventRepository.findByIdAndDeletedAtIsNull(ticket.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + ticket.getEventId() + " not found"));
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        return checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(ticketId).stream()
                .map(CheckInRecordResponse::from)
                .toList();
    }

    /**
     * BR-CHECKIN-001 (near-real-time, reuse-proof) + BR-TRANSFER-005's
     * payoff: {@code ticketCredentialService.verify} proves the signature
     * is genuine but says nothing about whether it's still CURRENT - a
     * ticket transferred/resold since this credential was issued has a
     * higher {@code credentialVersion} than what's embedded here, and that
     * mismatch is treated as invalid (the old credential is exactly what
     * BR-TRANSFER-005 requires to stop working). Locks the ticket row
     * ({@code findByIdForUpdate}) for the whole check-then-mark-used
     * sequence, same idiom as Phase 7/8's locking fixes - without it, two
     * concurrent scans of the same ticket could both read {@code VALID}
     * before either commits {@code USED}, letting the same ticket in twice.
     */
    private ScanOutcome resolveAndMarkTicket(String rawCredential, UUID eventId) {
        Optional<TicketCredentialService.ParsedCredential> parsed = ticketCredentialService.verify(rawCredential);
        if (parsed.isEmpty()) {
            return new ScanOutcome(null, CheckInResult.INVALID, null);
        }

        Ticket ticket = ticketRepository.findByIdForUpdate(parsed.get().ticketId()).orElse(null);
        if (ticket == null) {
            return new ScanOutcome(null, CheckInResult.INVALID, null);
        }
        if (ticket.getCredentialVersion() != parsed.get().version()) {
            // Superseded by a later transfer/resale - the presented
            // credential is genuine but no longer current.
            return new ScanOutcome(ticket.getId(), CheckInResult.INVALID, ticket);
        }
        if (!ticket.getEventId().equals(eventId)) {
            return new ScanOutcome(ticket.getId(), CheckInResult.WRONG_EVENT, ticket);
        }
        if (ticket.getStatus() == TicketStatus.USED) {
            return new ScanOutcome(ticket.getId(), CheckInResult.DUPLICATE, ticket);
        }
        if (ticket.getStatus() != TicketStatus.VALID) {
            return new ScanOutcome(ticket.getId(), CheckInResult.INVALID, ticket);
        }

        ticket.setStatus(TicketStatus.USED);
        ticketRepository.save(ticket);
        return new ScanOutcome(ticket.getId(), CheckInResult.VALID, ticket);
    }

    private TicketSummaryDto buildSummary(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        TicketType ticketType = ticketTypeRepository.findByIdAndDeletedAtIsNull(ticket.getTicketTypeId()).orElse(null);
        String seatDescription = null;
        if (ticket.getSeatId() != null) {
            Seat seat = seatRepository.findById(ticket.getSeatId()).orElse(null);
            if (seat != null) {
                seatDescription = "Section " + seat.getSection() + ", Row " + seat.getRow() + ", Seat " + seat.getSeatNumber();
            }
        }
        return new TicketSummaryDto(ticket.getTicketNumber(), ticketType != null ? ticketType.getName() : null, seatDescription);
    }

    /** Resolves the caller's OWN device (deviceAuth already proved it's ACTIVE) - re-fetched here only for its eventId. */
    private ScannerDevice requireOwnActiveDevice(UUID deviceId) {
        return scannerDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Scanner device " + deviceId + " not found"));
    }

    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the event's organizer/owner or an admin may view this ticket's check-in records");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record ScanOutcome(UUID ticketId, CheckInResult result, Ticket ticket) {
    }
}
