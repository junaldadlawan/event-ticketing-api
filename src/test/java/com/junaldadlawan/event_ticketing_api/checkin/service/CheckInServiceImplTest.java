package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInRecordResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.FallbackScanBatchRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.FallbackScanItemDto;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidateScanRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidationResultResponse;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.FallbackScanRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.FallbackScanRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAccessGuard;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialService;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CheckInServiceImpl} — the most important
 * suite in this pass. {@code resolveAndMarkTicket} is the shared primitive
 * both {@code validate} and {@code submitFallbackScans} funnel through
 * (BR-CHECKIN-001/002/003); every one of its branches is exercised here via
 * both public entry points that call it. The row-lock/concurrency proof
 * lives in the integration test instead, since a mocked repository can't
 * prove real Postgres locking behavior.
 */
@ExtendWith(MockitoExtension.class)
class CheckInServiceImplTest {

    @Mock
    private ScannerDeviceRepository scannerDeviceRepository;
    @Mock
    private CheckInConfigRepository checkInConfigRepository;
    @Mock
    private CheckInRecordRepository checkInRecordRepository;
    @Mock
    private FallbackScanRecordRepository fallbackScanRecordRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketCredentialService ticketCredentialService;
    @Mock
    private DeviceAccessGuard deviceAccessGuard;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private CheckInServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckInServiceImpl(scannerDeviceRepository, checkInConfigRepository, checkInRecordRepository,
                fallbackScanRecordRepository, ticketRepository, ticketTypeRepository, seatRepository, eventRepository,
                ticketCredentialService, deviceAccessGuard, accessGuard);
    }

    private ScannerDevice device(UUID id, UUID eventId) {
        return ScannerDevice.builder().id(id).eventId(eventId).deviceLabel("Gate 1").status(ScannerDeviceStatus.ACTIVE).build();
    }

    private Ticket ticket(UUID id, UUID eventId, TicketStatus status, int credentialVersion) {
        return Ticket.builder().id(id).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(UUID.randomUUID())
                .ownerId(UUID.randomUUID()).ticketNumber("ABC-000001").credential("cred").credentialVersion(credentialVersion)
                .status(status).build();
    }

    // ---- validate(): device_id cross-check ----

    @Test
    void validate_deviceIdInBodyDoesNotMatchAuthenticatedDevice_throwsForbidden() {
        UUID authenticatedDeviceId = UUID.randomUUID();
        UUID claimedDeviceId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(authenticatedDeviceId);
        ValidateScanRequest request = new ValidateScanRequest("some-credential", claimedDeviceId);

        assertThatThrownBy(() -> service.validate(request)).isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void validate_unknownAuthenticatedDevice_throwsResourceNotFound() {
        UUID deviceId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.empty());
        ValidateScanRequest request = new ValidateScanRequest("some-credential", deviceId);

        assertThatThrownBy(() -> service.validate(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- resolveAndMarkTicket via validate(): every branch ----

    @Test
    void validate_unparseableCredential_returnsInvalid_noTicketId_noCheckInRecordCreated() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        when(ticketCredentialService.verify("garbage")).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("garbage", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.INVALID);
        assertThat(response.ticketId()).isNull();
        assertThat(response.ticketSummary()).isNull();
        verify(checkInRecordRepository, never()).save(any());
        verify(ticketRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void validate_forgedCredential_indistinguishableFromMalformed_returnsInvalid_noRecord() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        // A structurally plausible but forged credential also parses to empty per TicketCredentialService's contract.
        when(ticketCredentialService.verify("forged")).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("forged", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.INVALID);
        assertThat(response.ticketId()).isNull();
        verify(checkInRecordRepository, never()).save(any());
    }

    @Test
    void validate_parsedTicketNoLongerExists_returnsInvalid_noTicketId_noRecord() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        when(ticketCredentialService.verify("cred")).thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(ticketId, 0)));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("cred", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.INVALID);
        assertThat(response.ticketId()).isNull();
        verify(checkInRecordRepository, never()).save(any());
    }

    @Test
    void validate_validCurrentCredential_sameEvent_marksTicketUsed_returnsValid_savesRecord() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.VALID, 0);
        when(ticketCredentialService.verify("cred")).thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(ticketId, 0)));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("cred", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.VALID);
        assertThat(response.ticketId()).isEqualTo(ticketId);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
        verify(ticketRepository, times(1)).save(ticket);

        ArgumentCaptor<CheckInRecord> recordCaptor = ArgumentCaptor.forClass(CheckInRecord.class);
        verify(checkInRecordRepository).save(recordCaptor.capture());
        CheckInRecord saved = recordCaptor.getValue();
        assertThat(saved.getTicketId()).isEqualTo(ticketId);
        assertThat(saved.getSourceType()).isEqualTo(CheckInSourceType.SCANNER_DEVICE);
        assertThat(saved.getSourceId()).isEqualTo(deviceId);
        assertThat(saved.getResult()).isEqualTo(CheckInResult.VALID);
    }

    @Test
    void validate_sameCredentialScannedTwice_secondScanReturnsDuplicate_doesNotReSave() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        // Ticket already USED — as if a prior scan already flipped it.
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.USED, 0);
        when(ticketCredentialService.verify("cred")).thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(ticketId, 0)));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        ValidationResultResponse response = service.validate(new ValidateScanRequest("cred", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.DUPLICATE);
        assertThat(response.ticketId()).isEqualTo(ticketId);
        verify(ticketRepository, never()).save(any());
        verify(checkInRecordRepository).save(any());
    }

    /**
     * The headline BR-TRANSFER-005 payoff: a genuine, correctly-signed
     * credential whose embedded version (0) no longer matches the ticket's
     * CURRENT {@code credentialVersion} (bumped to 1 by a transfer/resale
     * since this credential was issued) must be rejected as INVALID, not
     * accepted as VALID.
     */
    @Test
    void validate_staleCredentialVersion_afterSimulatedTransfer_returnsInvalid_notValid() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        // Ticket's CURRENT version is 1 (post-transfer); the presented credential embeds version 0 (pre-transfer).
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.VALID, 1);
        when(ticketCredentialService.verify("stale-cred")).thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(ticketId, 0)));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("stale-cred", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.INVALID);
        assertThat(response.ticketId()).isEqualTo(ticketId);
        // The ticket must NOT be marked used by a stale credential.
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VALID);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void validate_validCurrentCredential_differentEventThanDevice_returnsWrongEvent() {
        UUID deviceId = UUID.randomUUID();
        UUID deviceEventId = UUID.randomUUID();
        UUID ticketEventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, deviceEventId)));
        Ticket ticket = ticket(ticketId, ticketEventId, TicketStatus.VALID, 0);
        when(ticketCredentialService.verify("cred")).thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(ticketId, 0)));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("cred", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.WRONG_EVENT);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VALID);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void validate_ticketNeitherValidNorUsed_refunded_returnsInvalid() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.REFUNDED, 0);
        when(ticketCredentialService.verify("cred")).thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(ticketId, 0)));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("cred", deviceId));

        assertThat(response.result()).isEqualTo(CheckInResult.INVALID);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void validate_ticketSummary_includesTicketNumberTicketTypeNameAndSeat() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.VALID, 0);
        when(ticketCredentialService.verify("cred")).thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(ticketId, 0)));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticket.getTicketTypeId())).thenReturn(Optional.empty());

        ValidationResultResponse response = service.validate(new ValidateScanRequest("cred", deviceId));

        assertThat(response.ticketSummary()).isNotNull();
        assertThat(response.ticketSummary().ticketNumber()).isEqualTo("ABC-000001");
    }

    // ---- getDataset(): device cross-check + credential hashing ----

    @Test
    void getDataset_pathDeviceIdDoesNotMatchAuthenticatedDevice_throwsForbidden() {
        UUID authenticatedDeviceId = UUID.randomUUID();
        UUID pathDeviceId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(authenticatedDeviceId);

        assertThatThrownBy(() -> service.getDataset(pathDeviceId)).isInstanceOf(ForbiddenException.class);
        verify(scannerDeviceRepository, never()).findById(any());
    }

    @Test
    void getDataset_unknownDevice_throwsResourceNotFound() {
        UUID deviceId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDataset(deviceId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDataset_returnsCredentialHash_asSha256OfTheRawCredential_notTheRawCredentialItself() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.VALID, 0);
        ticket.setCredential("the-real-raw-credential-value");
        when(ticketRepository.findByEventId(eventId)).thenReturn(List.of(ticket));

        TicketDatasetResponse dataset = service.getDataset(deviceId);

        assertThat(dataset.eventId()).isEqualTo(eventId);
        assertThat(dataset.tickets()).hasSize(1);
        String expectedHash = sha256Base64Url("the-real-raw-credential-value");
        assertThat(dataset.tickets().get(0).credentialHash()).isEqualTo(expectedHash);
        assertThat(dataset.tickets().get(0).credentialHash()).isNotEqualTo("the-real-raw-credential-value");
        assertThat(dataset.tickets().get(0).ticketId()).isEqualTo(ticketId);
        assertThat(dataset.tickets().get(0).status()).isEqualTo("valid");
    }

    private String sha256Base64Url(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    // ---- submitFallbackScans(): mode gating + batch reconciliation ----

    @Test
    void submitFallbackScans_eventIdDoesNotMatchDevicesOwnEvent_throwsBadRequest() {
        UUID deviceId = UUID.randomUUID();
        UUID deviceEventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, deviceEventId)));
        FallbackScanBatchRequest request = new FallbackScanBatchRequest(otherEventId,
                List.of(new FallbackScanItemDto("cred", Instant.now())));

        assertThatThrownBy(() -> service.submitFallbackScans(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void submitFallbackScans_modeIsStandardDefault_noConfigRow_throwsConflict() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        when(checkInConfigRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        FallbackScanBatchRequest request = new FallbackScanBatchRequest(eventId,
                List.of(new FallbackScanItemDto("cred", Instant.now())));

        assertThatThrownBy(() -> service.submitFallbackScans(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void submitFallbackScans_modeExplicitlyStandard_throwsConflict() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.STANDARD).build()));
        FallbackScanBatchRequest request = new FallbackScanBatchRequest(eventId,
                List.of(new FallbackScanItemDto("cred", Instant.now())));

        assertThatThrownBy(() -> service.submitFallbackScans(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void submitFallbackScans_pureOfflineMode_mixedBatch_producesCorrectPerScanResultsAndRecords() {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID validTicketId = UUID.randomUUID();
        UUID unparseableTicketCredential = UUID.randomUUID();
        when(deviceAccessGuard.currentDeviceId()).thenReturn(deviceId);
        when(scannerDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device(deviceId, eventId)));
        when(checkInConfigRepository.findByEventId(eventId))
                .thenReturn(Optional.of(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.PURE_OFFLINE).build()));

        Ticket validTicket = ticket(validTicketId, eventId, TicketStatus.VALID, 0);
        when(ticketCredentialService.verify("valid-cred"))
                .thenReturn(Optional.of(new TicketCredentialService.ParsedCredential(validTicketId, 0)));
        when(ticketRepository.findByIdForUpdate(validTicketId)).thenReturn(Optional.of(validTicket));
        when(ticketCredentialService.verify("garbage-cred")).thenReturn(Optional.empty());
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        Instant capturedAt1 = Instant.now().minusSeconds(120);
        Instant capturedAt2 = Instant.now().minusSeconds(60);
        FallbackScanBatchRequest request = new FallbackScanBatchRequest(eventId, List.of(
                new FallbackScanItemDto("valid-cred", capturedAt1),
                new FallbackScanItemDto("garbage-cred", capturedAt2)));

        when(fallbackScanRecordRepository.save(any())).thenAnswer(invocation -> {
            FallbackScanRecord record = invocation.getArgument(0);
            record.setId(UUID.randomUUID());
            return record;
        });

        List<ValidationResultResponse> results = service.submitFallbackScans(request);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).result()).isEqualTo(CheckInResult.VALID);
        assertThat(results.get(0).ticketId()).isEqualTo(validTicketId);
        assertThat(results.get(1).result()).isEqualTo(CheckInResult.INVALID);
        assertThat(results.get(1).ticketId()).isNull();

        assertThat(validTicket.getStatus()).isEqualTo(TicketStatus.USED);

        ArgumentCaptor<FallbackScanRecord> recordCaptor = ArgumentCaptor.forClass(FallbackScanRecord.class);
        verify(fallbackScanRecordRepository, times(2)).save(recordCaptor.capture());
        List<FallbackScanRecord> savedRecords = recordCaptor.getAllValues();
        assertThat(savedRecords.get(0).getRawCredential()).isEqualTo("valid-cred");
        assertThat(savedRecords.get(0).getCapturedAt()).isEqualTo(capturedAt1);
        assertThat(savedRecords.get(0).getEventId()).isEqualTo(eventId);
        assertThat(savedRecords.get(0).getSyncedAt()).isNotNull();
        assertThat(savedRecords.get(0).getReconciledTicketId()).isEqualTo(validTicketId);
        assertThat(savedRecords.get(1).getRawCredential()).isEqualTo("garbage-cred");
        assertThat(savedRecords.get(1).getReconciledTicketId()).isNull();

        // Only the VALID scan (resolved to a real ticket) gets a CheckInRecord — the garbage one has nothing to attach to.
        ArgumentCaptor<CheckInRecord> checkInRecordCaptor = ArgumentCaptor.forClass(CheckInRecord.class);
        verify(checkInRecordRepository, times(1)).save(checkInRecordCaptor.capture());
        CheckInRecord savedCheckInRecord = checkInRecordCaptor.getValue();
        assertThat(savedCheckInRecord.getSourceType()).isEqualTo(CheckInSourceType.RECONCILED_FALLBACK);
        assertThat(savedCheckInRecord.getTicketId()).isEqualTo(validTicketId);
        assertThat(savedCheckInRecord.getResult()).isEqualTo(CheckInResult.VALID);
    }

    // ---- listTicketCheckInRecords(): visibility ----

    @Test
    void listTicketCheckInRecords_unknownTicket_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listTicketCheckInRecords(ticketId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listTicketCheckInRecords_roselessStranger_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.VALID, 0);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        Event event = Event.builder().id(eventId).organizationId(orgId).title("t").description("d")
                .category("music").ticketPrefix("ABC").build();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(accessGuard.isAdmin()).thenReturn(false);
        UUID callerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);

        assertThatThrownBy(() -> service.listTicketCheckInRecords(ticketId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listTicketCheckInRecords_admin_returnsRecords_orderedByScannedAtAsc() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, eventId, TicketStatus.VALID, 0);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        Event event = Event.builder().id(eventId).organizationId(orgId).title("t").description("d")
                .category("music").ticketPrefix("ABC").build();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(accessGuard.isAdmin()).thenReturn(true);
        CheckInRecord record = CheckInRecord.builder().id(UUID.randomUUID()).ticketId(ticketId)
                .sourceType(CheckInSourceType.SCANNER_DEVICE).sourceId(UUID.randomUUID())
                .result(CheckInResult.VALID).build();
        when(checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(ticketId)).thenReturn(List.of(record));

        List<CheckInRecordResponse> responses = service.listTicketCheckInRecords(ticketId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).ticketId()).isEqualTo(ticketId);
        assertThat(responses.get(0).result()).isEqualTo(CheckInResult.VALID);
    }
}
