package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceAuthorizeRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceResponse;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.checkin.security.ScannerDeviceCredentialService;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link ScannerDeviceServiceImpl} — BR-CHECKIN-008's
 * pure_offline single-active-device enforcement (with/without {@code
 * force_replace}), the STANDARD-mode/no-config "unlimited devices" default,
 * and the owner/organizer/admin authorization gate shared with {@code
 * CheckInConfigServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ScannerDeviceServiceImplTest {

    @Mock
    private ScannerDeviceRepository scannerDeviceRepository;
    @Mock
    private CheckInConfigRepository checkInConfigRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ScannerDeviceCredentialService credentialService;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private ScannerDeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScannerDeviceServiceImpl(scannerDeviceRepository, checkInConfigRepository, eventRepository, credentialService, accessGuard);
    }

    private Event event(UUID id, UUID orgId) {
        return Event.builder().id(id).organizationId(orgId).title("t").description("d").category("music").ticketPrefix("ABC").build();
    }

    private void stubAuthorizedCaller(UUID callerId, UUID orgId) {
        lenient().when(accessGuard.isAdmin()).thenReturn(false);
        lenient().when(accessGuard.currentUserId()).thenReturn(callerId);
        lenient().when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
    }

    // ---- authorize(): authorization gate ----

    @Test
    void authorize_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 1", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void authorize_roselessStranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        UUID callerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 1", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(scannerDeviceRepository, never()).save(any());
    }

    // ---- authorize(): STANDARD mode / no config row — unlimited devices ----

    @Test
    void authorize_noConfigRow_defaultsToStandard_unlimitedDevicesAllowed() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        stubAuthorizedCaller(callerId, orgId);
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(credentialService.generate(any())).thenReturn("generated-credential");
        when(scannerDeviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScannerDeviceResponse response = service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 1", null));

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.deviceLabel()).isEqualTo("Gate 1");
        assertThat(response.status()).isEqualTo(ScannerDeviceStatus.ACTIVE);
        assertThat(response.credential()).isEqualTo("generated-credential");
        // STANDARD mode never even checks for existing active devices.
        verify(scannerDeviceRepository, never()).findByEventIdAndStatus(any(), any());
    }

    @Test
    void authorize_explicitStandardMode_secondDeviceAllowed_noConflict() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        stubAuthorizedCaller(callerId, orgId);
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.STANDARD).build()));
        when(credentialService.generate(any())).thenReturn("cred");
        when(scannerDeviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScannerDeviceResponse response = service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 2", null));

        assertThat(response.status()).isEqualTo(ScannerDeviceStatus.ACTIVE);
        verify(scannerDeviceRepository, never()).findByEventIdAndStatus(any(), any());
    }

    // ---- authorize(): PURE_OFFLINE mode — BR-CHECKIN-008 single active device ----

    @Test
    void authorize_pureOfflineMode_noActiveDeviceYet_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        stubAuthorizedCaller(callerId, orgId);
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.PURE_OFFLINE).build()));
        when(scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(List.of());
        when(credentialService.generate(any())).thenReturn("cred");
        when(scannerDeviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScannerDeviceResponse response = service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 1", null));

        assertThat(response.status()).isEqualTo(ScannerDeviceStatus.ACTIVE);
    }

    @Test
    void authorize_pureOfflineMode_oneAlreadyActive_noForceReplace_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        stubAuthorizedCaller(callerId, orgId);
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.PURE_OFFLINE).build()));
        ScannerDevice existing = ScannerDevice.builder().id(UUID.randomUUID()).eventId(eventId)
                .deviceLabel("Existing").status(ScannerDeviceStatus.ACTIVE).build();
        when(scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 2", null)))
                .isInstanceOf(ConflictException.class);
        verify(scannerDeviceRepository, never()).save(any());
    }

    @Test
    void authorize_pureOfflineMode_oneAlreadyActive_explicitFalseForceReplace_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        stubAuthorizedCaller(callerId, orgId);
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.PURE_OFFLINE).build()));
        ScannerDevice existing = ScannerDevice.builder().id(UUID.randomUUID()).eventId(eventId)
                .deviceLabel("Existing").status(ScannerDeviceStatus.ACTIVE).build();
        when(scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 2", false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void authorize_pureOfflineMode_oneAlreadyActive_forceReplaceTrue_revokesExistingThenAuthorizesNew() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        stubAuthorizedCaller(callerId, orgId);
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.PURE_OFFLINE).build()));
        ScannerDevice existing = ScannerDevice.builder().id(UUID.randomUUID()).eventId(eventId)
                .deviceLabel("Existing").status(ScannerDeviceStatus.ACTIVE).build();
        when(scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(List.of(existing));
        when(credentialService.generate(any())).thenReturn("cred");
        when(scannerDeviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScannerDeviceResponse response = service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 2", true));

        assertThat(response.status()).isEqualTo(ScannerDeviceStatus.ACTIVE);
        assertThat(response.deviceLabel()).isEqualTo("Gate 2");
        assertThat(existing.getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
        // 2 saves: the revoked existing device, then the new active one.
        ArgumentCaptor<ScannerDevice> captor = ArgumentCaptor.forClass(ScannerDevice.class);
        verify(scannerDeviceRepository, times(2)).save(captor.capture());
        List<ScannerDevice> saved = captor.getAllValues();
        assertThat(saved.get(0)).isEqualTo(existing);
        assertThat(saved.get(0).getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
        assertThat(saved.get(1).getStatus()).isEqualTo(ScannerDeviceStatus.ACTIVE);
        assertThat(saved.get(1).getDeviceLabel()).isEqualTo("Gate 2");
    }

    @Test
    void authorize_pureOfflineMode_multipleActiveDevices_forceReplace_revokesAll() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        stubAuthorizedCaller(callerId, orgId);
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.PURE_OFFLINE).build()));
        ScannerDevice existing1 = ScannerDevice.builder().id(UUID.randomUUID()).eventId(eventId)
                .deviceLabel("Existing1").status(ScannerDeviceStatus.ACTIVE).build();
        ScannerDevice existing2 = ScannerDevice.builder().id(UUID.randomUUID()).eventId(eventId)
                .deviceLabel("Existing2").status(ScannerDeviceStatus.ACTIVE).build();
        when(scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).thenReturn(List.of(existing1, existing2));
        when(credentialService.generate(any())).thenReturn("cred");
        when(scannerDeviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.authorize(eventId, new ScannerDeviceAuthorizeRequest("Gate 3", true));

        assertThat(existing1.getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
        assertThat(existing2.getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
        verify(scannerDeviceRepository, times(3)).save(any());
    }

    // ---- revoke() ----

    @Test
    void revoke_unknownDevice_throwsResourceNotFound() {
        UUID deviceId = UUID.randomUUID();
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(deviceId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revoke_owningOrganizer_flipsStatusToRevoked_andSaves() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        ScannerDevice device = ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("Gate 1").status(ScannerDeviceStatus.ACTIVE).build();
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(scannerDeviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(deviceId);

        assertThat(device.getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
        verify(scannerDeviceRepository).save(device);
    }

    @Test
    void revoke_roselessStranger_throwsForbidden_deviceUnchanged() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        ScannerDevice device = ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("Gate 1").status(ScannerDeviceStatus.ACTIVE).build();
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.revoke(deviceId)).isInstanceOf(ForbiddenException.class);
        assertThat(device.getStatus()).isEqualTo(ScannerDeviceStatus.ACTIVE);
        verify(scannerDeviceRepository, never()).save(any());
    }

    @Test
    void revoke_admin_succeeds_withNoOrganizationMembershipAtAll() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        ScannerDevice device = ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("Gate 1").status(ScannerDeviceStatus.ACTIVE).build();
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(scannerDeviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(deviceId);

        assertThat(device.getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
    }
}
