package com.junaldadlawan.event_ticketing_api.checkin.history;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only scan history of an event for its team: owner, organizer with the CHECK_IN permission, CHECK_IN_STAFF of the event's organization, and
 * admins. Anyone else is refused. Only scan facts are returned, never who bought or owns a ticket.
 */
@Service
@RequiredArgsConstructor
public class CheckInHistoryService {

    private final CheckInHistoryRepository repository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Transactional(readOnly = true)
    public PageResponse<CheckInHistoryItem> history(UUID eventId, CheckInResult result, CheckInSourceType sourceType, UUID scannerDeviceId,
                                                    String ticketNumber, Pageable pageable) {
        requireAccess(eventId);
        CheckInHistoryRepository.Filter filter = filter(result, sourceType, scannerDeviceId, ticketNumber);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        long total = repository.count(eventId, filter);
        List<CheckInHistoryItem> items = repository.page(eventId, filter, size, (long) pageable.getPageNumber() * size);
        int pages = (int) ((total + size - 1) / size);
        return new PageResponse<>(items, pageable.getPageNumber(), size, total, pages, pageable.getPageNumber() + 1 >= pages);
    }

    @Transactional(readOnly = true)
    public CheckInSummaryResponse summary(UUID eventId, CheckInResult result, CheckInSourceType sourceType, UUID scannerDeviceId, String ticketNumber) {
        requireAccess(eventId);
        return repository.summary(eventId, filter(result, sourceType, scannerDeviceId, ticketNumber));
    }

    @Transactional(readOnly = true)
    public List<ScannerDeviceSummary> devices(UUID eventId) {
        requireAccess(eventId);
        return repository.devices(eventId);
    }

    private static CheckInHistoryRepository.Filter filter(CheckInResult result, CheckInSourceType sourceType, UUID deviceId, String ticketNumber) {
        String number = ticketNumber == null || ticketNumber.isBlank() ? null : ticketNumber.trim();
        return new CheckInHistoryRepository.Filter(result, sourceType, deviceId, number);
    }

    private void requireAccess(UUID eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID caller = accessGuard.currentUserId();
        UUID org = event.getOrganizationId();
        boolean allowed = accessGuard.hasRole(caller, org, OrganizationRole.OWNER)
                || accessGuard.hasPermission(caller, org, OrganizationPermission.CHECK_IN)
                || accessGuard.isCheckInStaff(caller, org);
        if (!allowed) {
            throw new ForbiddenException("Only the organization's owner, organizer, check-in staff or an admin may view this event's scans");
        }
    }
}
