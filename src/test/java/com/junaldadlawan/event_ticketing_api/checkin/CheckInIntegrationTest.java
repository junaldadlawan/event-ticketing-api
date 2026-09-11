package com.junaldadlawan.event_ticketing_api.checkin;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.FallbackScanRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.FallbackScanRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.checkin.security.ScannerDeviceCredentialService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialService;
import com.junaldadlawan.event_ticketing_api.tickettransfer.repository.TicketTransferRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code POST
 * /check-in/validate}, {@code POST /check-in/fallback-scans}, and {@code GET
 * /tickets/{ticketId}/check-in-records} against real Postgres + real signed
 * JWTs/device credentials. The headline scenario (dispatch item #1) is
 * {@link #validate_staleCredentialAfterRealTransfer_returnsInvalid_notValid}:
 * issue a real ticket, transfer it via the real {@code POST
 * /tickets/{id}/transfer} endpoint (bumping {@code credentialVersion}), then
 * scan the ORIGINAL pre-transfer credential and confirm it's rejected as
 * INVALID, not accepted. {@link
 * #validate_concurrentScansOfSameValidTicket_exactlyOneValidOneDuplicate_neverBothValid}
 * proves the {@code findByIdForUpdate} row lock with a genuine {@code
 * ExecutorService} race, same idiom as {@code WaitlistIntegrationTest}'s
 * headline test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckInIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ScannerDeviceCredentialService scannerDeviceCredentialService;
    @Autowired
    private TicketCredentialService ticketCredentialService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ScannerDeviceRepository scannerDeviceRepository;
    @Autowired
    private CheckInConfigRepository checkInConfigRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private CheckInRecordRepository checkInRecordRepository;
    @Autowired
    private FallbackScanRecordRepository fallbackScanRecordRepository;
    @Autowired
    private TicketTransferRepository ticketTransferRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdDeviceIds = new ArrayList<>();
    private final List<UUID> createdConfigIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdConfigIds) {
            checkInConfigRepository.deleteById(id);
        }
        createdConfigIds.clear();
        // Check-in records / fallback records / transfer history reference tickets - sweep by ticket id first.
        for (UUID ticketId : createdTicketIds) {
            checkInRecordRepository.findAll().stream()
                    .filter(r -> r.getTicketId().equals(ticketId))
                    .forEach(r -> checkInRecordRepository.deleteById(r.getId()));
            ticketTransferRepository.findByTicketIdOrderByTransferredAtAsc(ticketId)
                    .forEach(t -> ticketTransferRepository.deleteById(t.getId()));
        }
        for (UUID eventId : createdEventIds) {
            fallbackScanRecordRepository.findAll().stream()
                    .filter(r -> r.getEventId().equals(eventId))
                    .forEach(r -> fallbackScanRecordRepository.deleteById(r.getId()));
        }
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
        for (UUID id : createdDeviceIds) {
            scannerDeviceRepository.deleteById(id);
        }
        createdDeviceIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID id : createdOrgIds) {
            organizationRepository.deleteById(id);
        }
        createdOrgIds.clear();
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    private User inMemoryUser(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private User persistUser(Role role) {
        User user = User.builder().name("CheckIn Test User").email("checkin-" + UUID.randomUUID() + "@test.local")
                .passwordHash("irrelevant").role(role).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder().name("CheckIn Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(ownerId).documents(List.of()).build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder().organizationId(organizationId).title("CheckIn Test Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED)
                .ticketPrefix("V" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private void setMode(UUID eventId, CheckInMode mode) {
        CheckInConfig config = checkInConfigRepository.findByEventId(eventId)
                .orElseGet(() -> CheckInConfig.builder().eventId(eventId).mode(mode).build());
        config.setMode(mode);
        CheckInConfig saved = checkInConfigRepository.save(config);
        createdConfigIds.add(saved.getId());
    }

    private String persistActiveDevice(UUID eventId) {
        UUID deviceId = UUID.randomUUID();
        ScannerDevice device = ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("Test Gate")
                .status(ScannerDeviceStatus.ACTIVE).createdBy("test").build();
        ScannerDevice saved = scannerDeviceRepository.save(device);
        createdDeviceIds.add(saved.getId());
        return scannerDeviceCredentialService.generate(deviceId);
    }

    private Ticket persistTicket(UUID eventId, UUID ownerId, TicketStatus status) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().id(ticketId).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(UUID.randomUUID())
                .seatId(null).ownerId(ownerId).ticketNumber("CHK-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential(ticketCredentialService.generate(ticketId, 0)).credentialVersion(0).status(status).build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
        return saved;
    }

    // ---- POST /check-in/validate — SecurityConfig matcher (device-only) ----

    @Test
    void validate_userJwtInstead_returns403() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User user = persistUser(Role.CUSTOMER);
        String userToken = jwtService.generateAccessToken(user);
        UUID someDeviceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content("{\"credential\":\"whatever\",\"deviceId\":\"" + someDeviceId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validate_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/check-in/validate")
                        .contentType("application/json")
                        .content("{\"credential\":\"whatever\",\"deviceId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- resolveAndMarkTicket's branches, end-to-end with real credentials ----

    @Test
    void validate_genuineCurrentCredential_sameEvent_returns200_valid_marksTicketUsed_savesRecord() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"" + ticket.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.result").value("VALID"))
                .andExpect(jsonPath("$.ticketSummary.ticketNumber").value(ticket.getTicketNumber()));

        Ticket refreshed = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(TicketStatus.USED);
        List<CheckInRecord> records = checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(ticket.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getSourceId()).isEqualTo(deviceId);
    }

    @Test
    void validate_sameCredentialScannedTwice_secondScanReturnsDuplicate() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();
        String body = "{\"credential\":\"" + ticket.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}";

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("VALID"));

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));

        assertThat(checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(ticket.getId())).hasSize(2);
    }

    @Test
    void validate_unparseableCredential_returnsInvalid_noTicketId() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"garbage-not-a-real-credential\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("INVALID"))
                .andExpect(jsonPath("$.ticketId").doesNotExist());
    }

    @Test
    void validate_ticketBelongsToDifferentEventThanDevice_returnsWrongEvent() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID ticketEventId = persistEvent(orgId);
        UUID deviceEventId = persistEvent(orgId);
        Ticket ticket = persistTicket(ticketEventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(deviceEventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"" + ticket.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("WRONG_EVENT"));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.VALID);
    }

    @Test
    void validate_deviceIdInBodyDoesNotMatchAuthenticatedDevice_returns403() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);
        UUID differentDeviceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"" + ticket.getCredential() + "\",\"deviceId\":\"" + differentDeviceId + "\"}"))
                .andExpect(status().isForbidden());

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.VALID);
    }

    /**
     * THE headline scenario (dispatch item #1): a genuine, correctly-signed
     * credential whose embedded version no longer matches the ticket's
     * CURRENT credentialVersion — because the ticket was genuinely
     * transferred via the real {@code POST /tickets/{id}/transfer} endpoint
     * since this credential was issued — must be rejected as INVALID.
     */
    @Test
    void validate_staleCredentialAfterRealTransfer_returnsInvalid_notValid() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User originalOwner = persistUser(Role.CUSTOMER);
        User newOwner = persistUser(Role.CUSTOMER);
        Ticket ticket = persistTicket(eventId, originalOwner.getId(), TicketStatus.VALID);
        String originalCredential = ticket.getCredential();
        String ownerToken = jwtService.generateAccessToken(originalOwner);

        // Real transfer via the real Phase 7 endpoint - bumps credentialVersion and reissues the credential.
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + newOwner.getId() + "\"}"))
                .andExpect(status().isOk());

        Ticket refreshedAfterTransfer = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedAfterTransfer.getCredentialVersion()).isEqualTo(1);
        assertThat(refreshedAfterTransfer.getCredential()).isNotEqualTo(originalCredential);

        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();

        // Scan the ORIGINAL pre-transfer credential - must be rejected, not accepted.
        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"" + originalCredential + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("INVALID"));

        Ticket refreshedAfterScan = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedAfterScan.getStatus())
                .as("the stale credential must never flip the ticket to USED")
                .isEqualTo(TicketStatus.VALID);

        // The NEW (current) credential, by contrast, scans as genuinely VALID.
        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"" + refreshedAfterTransfer.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("VALID"));
    }

    /**
     * THE concurrency proof (dispatch item #1's row-lock requirement): two
     * real threads scanning the SAME valid ticket's SAME credential at the
     * same instant must resolve to exactly one VALID and one DUPLICATE,
     * never both VALID — proving {@code findByIdForUpdate}'s row lock
     * actually serializes the check-then-mark-used sequence. Same idiom as
     * {@code WaitlistIntegrationTest}'s headline concurrency test.
     */
    @Test
    void validate_concurrentScansOfSameValidTicket_exactlyOneValidOneDuplicate_neverBothValid() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();
        String body = "{\"credential\":\"" + ticket.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}";

        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<String>> tasks = IntStream.range(0, threadCount)
                    .<Callable<String>>mapToObj(i -> () -> {
                        startLatch.await();
                        var result = mockMvc.perform(post("/api/v1/check-in/validate")
                                        .header("Authorization", "Bearer " + deviceCredential)
                                        .contentType("application/json")
                                        .content(body))
                                .andReturn();
                        return objectMapperResult(result.getResponse().getContentAsString());
                    })
                    .collect(Collectors.toList());

            List<Future<String>> futures = tasks.stream().map(executor::submit).collect(Collectors.toList());
            startLatch.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }

            long validCount = results.stream().filter(r -> r.equals("VALID")).count();
            long duplicateCount = results.stream().filter(r -> r.equals("DUPLICATE")).count();
            assertThat(validCount).as("exactly one concurrent scan must win as VALID, got results=%s", results).isEqualTo(1);
            assertThat(duplicateCount).as("every other concurrent scan must lose as DUPLICATE, got results=%s", results).isEqualTo(threadCount - 1);
        } finally {
            executor.shutdown();
        }

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(ticket.getId())).hasSize(threadCount);
    }

    private String objectMapperResult(String json) {
        // Cheap extraction to avoid pulling in a shared ObjectMapper field just for one field.
        int idx = json.indexOf("\"result\":\"");
        int start = idx + "\"result\":\"".length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    // ---- POST /check-in/fallback-scans ----

    @Test
    void submitFallbackScans_userJwtInstead_returns403() throws Exception {
        User user = persistUser(Role.CUSTOMER);
        String userToken = jwtService.generateAccessToken(user);

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"scans\":["
                                + "{\"rawCredential\":\"x\",\"capturedAt\":\"2026-01-01T00:00:00Z\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitFallbackScans_notPureOfflineMode_returns409() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        String deviceCredential = persistActiveDevice(eventId);

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + eventId + "\",\"scans\":["
                                + "{\"rawCredential\":\"x\",\"capturedAt\":\"2026-01-01T00:00:00Z\"}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void submitFallbackScans_eventIdDoesNotMatchDevicesOwnEvent_returns400() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID deviceEventId = persistEvent(orgId);
        UUID otherEventId = persistEvent(orgId);
        setMode(deviceEventId, CheckInMode.PURE_OFFLINE);
        String deviceCredential = persistActiveDevice(deviceEventId);

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + otherEventId + "\",\"scans\":["
                                + "{\"rawCredential\":\"x\",\"capturedAt\":\"2026-01-01T00:00:00Z\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitFallbackScans_pureOfflineMode_mixedBatch_returns200_withCorrectPerScanResults_andReconciliationRows() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        setMode(eventId, CheckInMode.PURE_OFFLINE);
        Ticket validTicket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + eventId + "\",\"scans\":["
                                + "{\"rawCredential\":\"" + validTicket.getCredential() + "\",\"capturedAt\":\"2026-01-01T00:00:00Z\"},"
                                + "{\"rawCredential\":\"garbage-credential\",\"capturedAt\":\"2026-01-01T00:01:00Z\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].result").value("VALID"))
                .andExpect(jsonPath("$[0].ticketId").value(validTicket.getId().toString()))
                .andExpect(jsonPath("$[1].result").value("INVALID"))
                .andExpect(jsonPath("$[1].ticketId").doesNotExist());

        assertThat(ticketRepository.findById(validTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.USED);
        List<FallbackScanRecord> fallbackRecords = fallbackScanRecordRepository.findAll().stream()
                .filter(r -> r.getEventId().equals(eventId)).toList();
        assertThat(fallbackRecords).hasSize(2);
        assertThat(fallbackRecords).anySatisfy(r -> {
            assertThat(r.getRawCredential()).isEqualTo(validTicket.getCredential());
            assertThat(r.getReconciledTicketId()).isEqualTo(validTicket.getId());
            assertThat(r.getSyncedAt()).isNotNull();
        });
        assertThat(fallbackRecords).anySatisfy(r -> {
            assertThat(r.getRawCredential()).isEqualTo("garbage-credential");
            assertThat(r.getReconciledTicketId()).isNull();
        });
        // Only the resolved scan gets a CheckInRecord (RECONCILED_FALLBACK source).
        List<CheckInRecord> checkInRecords = checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(validTicket.getId());
        assertThat(checkInRecords).hasSize(1);
        assertThat(checkInRecords.get(0).getSourceType().name()).isEqualTo("RECONCILED_FALLBACK");
    }

    /**
     * BR-CHECKIN-010: a duplicate WITHIN a reconciled fallback batch (the
     * same credential captured twice offline, e.g. two different fallback
     * recorders, or the same one scanning twice) can't be prevented in real
     * time but must still be surfaced — both immediately in the per-scan
     * response array (openapi: "duplicates are flagged, not resolved
     * automatically") AND persisted as a CheckInRecord for the organizer's
     * after-the-fact review via GET /tickets/{id}/check-in-records.
     */
    @Test
    void submitFallbackScans_sameCredentialTwiceInOneBatch_secondFlaggedDuplicate_bothPersistedForReview() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        setMode(eventId, CheckInMode.PURE_OFFLINE);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + eventId + "\",\"scans\":["
                                + "{\"rawCredential\":\"" + ticket.getCredential() + "\",\"capturedAt\":\"2026-01-01T00:00:00Z\"},"
                                + "{\"rawCredential\":\"" + ticket.getCredential() + "\",\"capturedAt\":\"2026-01-01T00:05:00Z\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].result").value("VALID"))
                .andExpect(jsonPath("$[1].result").value("DUPLICATE"))
                .andExpect(jsonPath("$[1].ticketId").value(ticket.getId().toString()));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.USED);
        // Both the VALID and the DUPLICATE scan are persisted for organizer review, not silently dropped.
        List<CheckInRecord> checkInRecords = checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(ticket.getId());
        assertThat(checkInRecords).hasSize(2);
        assertThat(checkInRecords).extracting(r -> r.getResult().name()).containsExactly("VALID", "DUPLICATE");
        List<FallbackScanRecord> fallbackRecords = fallbackScanRecordRepository.findAll().stream()
                .filter(r -> r.getEventId().equals(eventId)).toList();
        assertThat(fallbackRecords).hasSize(2);
        assertThat(fallbackRecords).allSatisfy(r -> assertThat(r.getReconciledTicketId()).isEqualTo(ticket.getId()));
    }

    // ---- GET /tickets/{ticketId}/check-in-records ----

    @Test
    void listCheckInRecords_owningOrganizer_returns200_withRealScanHistory() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();
        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"" + ticket.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk());
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/check-in-records", ticket.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$[0].result").value("VALID"))
                .andExpect(jsonPath("$[0].sourceType").value("SCANNER_DEVICE"));
    }

    @Test
    void listCheckInRecords_roselessStranger_returns403() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        User stranger = persistUser(Role.CUSTOMER);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/check-in-records", ticket.getId())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCheckInRecords_admin_returns200() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/check-in-records", ticket.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listCheckInRecords_unknownTicket_returns404() throws Exception {
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/check-in-records", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
