package com.junaldadlawan.event_ticketing_api.platformsetting.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformsetting.dto.TicketSaleSettingsRequest;
import com.junaldadlawan.event_ticketing_api.platformsetting.dto.TicketSaleSettingsResponse;
import com.junaldadlawan.event_ticketing_api.platformsetting.entity.TicketSaleSettings;
import com.junaldadlawan.event_ticketing_api.platformsetting.repository.TicketSaleSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketSaleSettingsServiceImpl implements TicketSaleSettingsService {

    private final TicketSaleSettingsRepository settingsRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    @Override
    public TicketSaleSettings current() {
        return settingsRepository.findById(TicketSaleSettings.ROW_ID).orElseGet(TicketSaleSettings::new);
    }

    @Override
    public TicketSaleSettingsResponse get() {
        accessGuard.currentUserId();   // signed in
        return TicketSaleSettingsResponse.from(current());
    }

    @Override
    @Transactional
    public TicketSaleSettingsResponse update(TicketSaleSettingsRequest request) {
        accessGuard.requireAdmin();
        TicketSaleSettings settings = current();
        Map<String, Object> before = AuditDetails.snapshot(settings, TicketSaleSettingsRequest.class);
        settings.setSalesCloseMinutesBeforeEvent(request.salesCloseMinutesBeforeEvent());
        settings.setMinSaleDurationMinutes(request.minSaleDurationMinutes());
        TicketSaleSettings saved = settingsRepository.save(settings);
        Map<String, Object> changes = AuditDetails.changes(before, AuditDetails.snapshot(saved, TicketSaleSettingsRequest.class));
        if (changes != null) {
            // a single settings row: no target id
            auditLogService.recordByCurrentUser("ticket_sale_settings.updated", "TicketSaleSettings", null, null, null, changes);
        }
        return TicketSaleSettingsResponse.from(saved);
    }
}
