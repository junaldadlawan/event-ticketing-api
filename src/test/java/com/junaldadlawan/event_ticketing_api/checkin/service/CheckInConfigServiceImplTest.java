package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigUpdateRequest;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CheckInConfigServiceImpl} — the
 * get/upsert-with-default behavior and, specifically, {@code
 * computeModeSwitchWarning}'s two INDEPENDENTLY required conditions
 * (mode actually changing AND at least one ACTIVE device existing).
 */
@ExtendWith(MockitoExtension.class)
class CheckInConfigServiceImplTest {

    @Mock
    private CheckInConfigRepository checkInConfigRepository;
    @Mock
    private ScannerDeviceRepository scannerDeviceRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private CheckInConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckInConfigServiceImpl(checkInConfigRepository, scannerDeviceRepository, eventRepository, accessGuard);
    }

    private Event event(UUID id, UUID orgId) {
        return Event.builder().id(id).organizationId(orgId).title("t").description("d").category("music").ticketPrefix("ABC").build();
    }

    // ---- get() ----

    @Test
    void get_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(eventId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_noConfigRowYet_returnsStandardDefault_300Seconds() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        CheckInConfigResponse response = service.get(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.mode()).isEqualTo(CheckInMode.STANDARD);
        assertThat(response.offlineFallbackExpirySeconds()).isEqualTo(300);
        assertThat(response.warning()).isNull();
    }

    @Test
    void get_existingConfigRow_returnsPersistedValues() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        CheckInConfig config = CheckInConfig.builder().id(UUID.randomUUID()).eventId(eventId)
                .mode(CheckInMode.PURE_OFFLINE).offlineFallbackExpirySeconds(600).build();
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.of(config));

        CheckInConfigResponse response = service.get(eventId);

        assertThat(response.mode()).isEqualTo(CheckInMode.PURE_OFFLINE);
        assertThat(response.offlineFallbackExpirySeconds()).isEqualTo(600);
        assertThat(response.warning()).isNull();
    }

    // ---- update(): authorization gate ----

    @Test
    void update_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(eventId, new CheckInConfigUpdateRequest(CheckInMode.PURE_OFFLINE, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_roselessStranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(eventId, new CheckInConfigUpdateRequest(CheckInMode.PURE_OFFLINE, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(checkInConfigRepository, never()).save(any());
    }

    // ---- update(): first-time upsert (no existing row) ----

    @Test
    void update_noExistingRow_createsNewConfig_defaultsPreviousModeToStandard() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(scannerDeviceRepository.countByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(0L);
        when(checkInConfigRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CheckInConfig c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CheckInConfigResponse response = service.update(eventId, new CheckInConfigUpdateRequest(CheckInMode.PURE_OFFLINE, 600));

        assertThat(response.mode()).isEqualTo(CheckInMode.PURE_OFFLINE);
        assertThat(response.offlineFallbackExpirySeconds()).isEqualTo(600);
        // Changing from the implicit STANDARD default but zero active devices -> no warning.
        assertThat(response.warning()).isNull();
        verify(checkInConfigRepository).saveAndFlush(any());
        verify(checkInConfigRepository, never()).save(any());
    }

    @Test
    void update_noExistingRow_raceLostToConcurrentFirstPatch_recoversViaFindByEventId() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        // First read: no row yet. Second read (recovery path): the winner's row.
        CheckInConfig winner = CheckInConfig.builder().id(UUID.randomUUID()).eventId(eventId).mode(CheckInMode.STANDARD).build();
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.empty(), Optional.of(winner));
        when(scannerDeviceRepository.countByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(0L);
        when(checkInConfigRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_check_in_configs_event_id"));
        when(checkInConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CheckInConfigResponse response = service.update(eventId, new CheckInConfigUpdateRequest(CheckInMode.PURE_OFFLINE, null));

        assertThat(response.mode()).isEqualTo(CheckInMode.PURE_OFFLINE);
        verify(checkInConfigRepository).save(winner);
    }

    // ---- update(): mode-switch warning — the two independently-required conditions ----

    @Test
    void update_modeActuallyChanges_atLeastOneActiveDevice_returnsWarning() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        CheckInConfig existing = CheckInConfig.builder().id(UUID.randomUUID()).eventId(eventId).mode(CheckInMode.STANDARD).build();
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(scannerDeviceRepository.countByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(1L);
        when(checkInConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CheckInConfigResponse response = service.update(eventId, new CheckInConfigUpdateRequest(CheckInMode.PURE_OFFLINE, null));

        assertThat(response.mode()).isEqualTo(CheckInMode.PURE_OFFLINE);
        assertThat(response.warning()).isNotNull().contains("Switching check-in modes");
    }

    @Test
    void update_modeActuallyChanges_zeroActiveDevices_noWarning() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        CheckInConfig existing = CheckInConfig.builder().id(UUID.randomUUID()).eventId(eventId).mode(CheckInMode.STANDARD).build();
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(scannerDeviceRepository.countByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(0L);
        when(checkInConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CheckInConfigResponse response = service.update(eventId, new CheckInConfigUpdateRequest(CheckInMode.PURE_OFFLINE, null));

        assertThat(response.warning()).isNull();
    }

    @Test
    void update_sameModeAsBefore_atLeastOneActiveDevice_noWarning() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        CheckInConfig existing = CheckInConfig.builder().id(UUID.randomUUID()).eventId(eventId).mode(CheckInMode.STANDARD).offlineFallbackExpirySeconds(300).build();
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(checkInConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // PATCHing with the SAME mode (just changing the expiry) while devices exist -> no warning.
        CheckInConfigResponse response = service.update(eventId, new CheckInConfigUpdateRequest(CheckInMode.STANDARD, 900));

        assertThat(response.mode()).isEqualTo(CheckInMode.STANDARD);
        assertThat(response.offlineFallbackExpirySeconds()).isEqualTo(900);
        assertThat(response.warning()).isNull();
        // Same-mode short-circuit never even queries for active devices.
        verify(scannerDeviceRepository, never()).countByEventIdAndStatus(any(), any());
    }

    @Test
    void update_omittedMode_updatesOnlyExpiry_modeUnchanged() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        CheckInConfig existing = CheckInConfig.builder().id(UUID.randomUUID()).eventId(eventId).mode(CheckInMode.PURE_OFFLINE).offlineFallbackExpirySeconds(300).build();
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(checkInConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CheckInConfigResponse response = service.update(eventId, new CheckInConfigUpdateRequest(null, 120));

        assertThat(response.mode()).isEqualTo(CheckInMode.PURE_OFFLINE);
        assertThat(response.offlineFallbackExpirySeconds()).isEqualTo(120);
        assertThat(response.warning()).isNull();
    }

    @Test
    void update_setsUpdatedByToCallerId() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        CheckInConfig existing = CheckInConfig.builder().id(UUID.randomUUID()).eventId(eventId).mode(CheckInMode.STANDARD).build();
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(checkInConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(eventId, new CheckInConfigUpdateRequest(null, 90));

        assertThat(existing.getUpdatedBy()).isEqualTo(callerId.toString());
    }
}
