package com.junaldadlawan.event_ticketing_api.checkin.repository;

import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, UUID> {

    /** {@code GET /tickets/{ticketId}/check-in-records} - full scan-attempt audit trail. */
    List<CheckInRecord> findByTicketIdOrderByScannedAtAsc(UUID ticketId);
}
