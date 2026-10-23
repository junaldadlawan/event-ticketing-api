package com.junaldadlawan.event_ticketing_api.platformsetting.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.event.service.DraftAutoDelete;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformsetting.dto.DraftEventSettingsRequest;
import com.junaldadlawan.event_ticketing_api.platformsetting.dto.DraftEventSettingsResponse;
import com.junaldadlawan.event_ticketing_api.platformsetting.entity.DraftEventSettings;
import com.junaldadlawan.event_ticketing_api.platformsetting.repository.DraftEventSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** BR-EVENT-007: the cutoff for auto-deleting unpublished drafts. Any signed-in user reads it (the apps show the deadline); admins change it. */
@Service
@RequiredArgsConstructor
public class DraftEventSettingsService {

    private final DraftEventSettingsRepository repository;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    /** The rule in force now (the seeded 60 if the row is somehow missing). Also refreshes what event responses use for {@code autoDeleteAt}. */
    public DraftEventSettings current() {
        DraftEventSettings settings = repository.findById(DraftEventSettings.ROW_ID).orElseGet(DraftEventSettings::new);
        DraftAutoDelete.setMinutesBeforeStart(settings.getAutoDeleteMinutesBeforeStart());
        return settings;
    }

    @Transactional(readOnly = true)
    public DraftEventSettingsResponse get() {
        accessGuard.currentUserId();   // signed in
        return DraftEventSettingsResponse.from(current());
    }

    @Transactional
    public DraftEventSettingsResponse update(DraftEventSettingsRequest request) {
        accessGuard.requireAdmin();
        DraftEventSettings settings = current();
        Map<String, Object> before = AuditDetails.snapshot(settings, DraftEventSettingsRequest.class);
        settings.setAutoDeleteMinutesBeforeStart(request.autoDeleteMinutesBeforeStart());
        DraftEventSettings saved = repository.save(settings);
        DraftAutoDelete.setMinutesBeforeStart(saved.getAutoDeleteMinutesBeforeStart());
        Map<String, Object> changes = AuditDetails.changes(before, AuditDetails.snapshot(saved, DraftEventSettingsRequest.class));
        if (changes != null) {
            auditLogService.recordByCurrentUser("draft_event_settings.updated", "DraftEventSettings", null, null, null, changes);
        }
        return DraftEventSettingsResponse.from(saved);
    }
}
