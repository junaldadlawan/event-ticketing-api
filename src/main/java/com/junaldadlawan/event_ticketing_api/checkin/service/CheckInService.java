package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInRecordResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.FallbackScanBatchRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidateScanRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidationResultResponse;

import java.util.List;
import java.util.UUID;

public interface CheckInService {

    /** {@code GET /scanner-devices/{deviceId}/dataset} - device-authenticated, the caller's own device only. */
    TicketDatasetResponse getDataset(UUID deviceId);

    /** {@code POST /check-in/validate} - device-authenticated (BR-CHECKIN-001/002/003). */
    ValidationResultResponse validate(ValidateScanRequest request);

    /** {@code POST /check-in/fallback-scans} - device-authenticated, pure_offline mode only (BR-CHECKIN-009/010). */
    List<ValidationResultResponse> submitFallbackScans(FallbackScanBatchRequest request);

    /** {@code GET /tickets/{ticketId}/check-in-records} - owning organizer/owner or admin. */
    List<CheckInRecordResponse> listTicketCheckInRecords(UUID ticketId);
}
