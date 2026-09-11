package com.junaldadlawan.event_ticketing_api.checkin.repository;

import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScannerDeviceRepository extends JpaRepository<ScannerDevice, UUID> {

    List<ScannerDevice> findByEventIdAndStatus(UUID eventId, ScannerDeviceStatus status);

    long countByEventIdAndStatus(UUID eventId, ScannerDeviceStatus status);
}
