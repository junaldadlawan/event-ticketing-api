package com.junaldadlawan.event_ticketing_api.checkin.repository;

import com.junaldadlawan.event_ticketing_api.checkin.entity.FallbackScanRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FallbackScanRecordRepository extends JpaRepository<FallbackScanRecord, UUID> {
}
