package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketCheckInResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The holder-facing {@code Ticket.checkIn}: for each USED ticket, the valid scan that checked it in. Duplicate,
 * invalid and wrong_event attempts stay in the organizer-only {@code GET /tickets/{id}/check-in-records}.
 */
@Component
@RequiredArgsConstructor
public class TicketCheckInLookup {

    private final CheckInRecordRepository checkInRecordRepository;
    private final ScannerDeviceRepository scannerDeviceRepository;

    /** Ticket id -> its check-in, for the USED ones only; one query for the records and one for the device names. */
    public Map<UUID, TicketCheckInResponse> forTickets(Collection<Ticket> tickets) {
        List<UUID> usedIds = tickets.stream().filter(t -> t.getStatus() == TicketStatus.USED).map(Ticket::getId).toList();
        if (usedIds.isEmpty()) {
            return Map.of();
        }
        List<CheckInRecord> records = checkInRecordRepository.findByTicketIdInAndResultOrderByScannedAtAsc(usedIds, CheckInResult.VALID);
        List<UUID> deviceIds = records.stream().filter(r -> r.getSourceType() == CheckInSourceType.SCANNER_DEVICE)
                .map(CheckInRecord::getSourceId).distinct().toList();
        Map<UUID, String> deviceNames = scannerDeviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(ScannerDevice::getId, ScannerDevice::getDeviceLabel));

        Map<UUID, TicketCheckInResponse> checkIns = new HashMap<>();
        for (CheckInRecord r : records) {
            // ordered by scan time, so the first valid scan wins should there ever be more than one
            checkIns.putIfAbsent(r.getTicketId(), new TicketCheckInResponse(r.getScannedAt(), r.getScannerLocation(),
                    r.getSourceId(), r.getSourceType() == CheckInSourceType.SCANNER_DEVICE ? deviceNames.get(r.getSourceId()) : null));
        }
        return checkIns;
    }

    public TicketCheckInResponse forTicket(Ticket ticket) {
        return forTickets(List.of(ticket)).get(ticket.getId());
    }
}
