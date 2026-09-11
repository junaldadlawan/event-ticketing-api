package com.junaldadlawan.event_ticketing_api.checkin;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInConfigRepository;
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
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code POST
 * /events/{eventId}/scanner-devices}, {@code DELETE /scanner-devices/{id}},
 * and {@code GET /scanner-devices/{id}/dataset} against real Postgres +
 * real signed JWTs/device credentials. Proves BR-CHECKIN-008's pure_offline
 * single-active-device gate end-to-end (dispatch item #3), that revocation
 * is immediate and checked live (item #4 — a revoked device's own previously
 * valid credential must 401, not 403, on all three device-only endpoints),
 * the {@code deviceId} cross-check on {@code GET .../dataset} (item #2), and
 * that {@code credentialHash} is a real SHA-256 digest of the ticket's
 * credential column, never the raw credential (item #5).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScannerDeviceIntegrationTest {

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
    private ObjectMapper objectMapper;

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
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
        // Sweep any devices created indirectly via POST (not individually tracked).
        for (UUID eventId : createdEventIds) {
            scannerDeviceRepository.findAll().stream()
                    .filter(d -> d.getEventId().equals(eventId))
                    .forEach(d -> scannerDeviceRepository.deleteById(d.getId()));
        }
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
        User user = User.builder().name("ScannerDevice Test User").email("scanner-device-" + UUID.randomUUID() + "@test.local")
                .passwordHash("irrelevant").role(role).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder().name("ScannerDevice Test Org " + UUID.randomUUID())
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
        Event event = Event.builder().organizationId(organizationId).title("ScannerDevice Test Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED)
                .ticketPrefix("S" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
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
                .seatId(null).ownerId(ownerId).ticketNumber("SCN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential(ticketCredentialService.generate(ticketId, 0)).credentialVersion(0).status(status).build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
        return saved;
    }

    // ---- POST /events/{eventId}/scanner-devices — authorization gate ----

    @Test
    void authorize_owner_returns201_withCredential() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Main Gate\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.deviceLabel").value("Main Gate"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.credential").isNotEmpty());
    }

    @Test
    void authorize_roselessStranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Main Gate\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorize_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Main Gate\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- BR-CHECKIN-008: pure_offline single-active-device enforcement ----

    @Test
    void authorize_pureOfflineMode_secondDeviceWithoutForceReplace_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        setMode(eventId, CheckInMode.PURE_OFFLINE);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 2\"}"))
                .andExpect(status().isConflict());

        List<ScannerDevice> devices = scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE);
        assertThat(devices).hasSize(1);
    }

    @Test
    void authorize_pureOfflineMode_forceReplace_revokesExistingAndAuthorizesNew() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        setMode(eventId, CheckInMode.PURE_OFFLINE);
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult first = mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        UUID firstDeviceId = UUID.fromString(firstBody.get("id").asText());

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 2\",\"forceReplace\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ScannerDevice firstRefreshed = scannerDeviceRepository.findById(firstDeviceId).orElseThrow();
        assertThat(firstRefreshed.getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
        List<ScannerDevice> activeDevices = scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE);
        assertThat(activeDevices).hasSize(1);
        assertThat(activeDevices.get(0).getDeviceLabel()).isEqualTo("Gate 2");
    }

    @Test
    void authorize_standardMode_multipleDevicesAllowed_noConflict() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        // No CheckInConfig row at all -> defaults to STANDARD.
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 1\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 2\"}"))
                .andExpect(status().isCreated());

        assertThat(scannerDeviceRepository.findByEventIdAndStatus(eventId, ScannerDeviceStatus.ACTIVE)).hasSize(2);
    }

    // ---- DELETE /scanner-devices/{deviceId} — revocation is immediate ----

    @Test
    void revoke_owner_returns204_deviceStatusBecomesRevoked() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID deviceId = UUID.randomUUID();
        ScannerDevice device = ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("Gate 1")
                .status(ScannerDeviceStatus.ACTIVE).createdBy("test").build();
        ScannerDevice saved = scannerDeviceRepository.save(device);
        createdDeviceIds.add(saved.getId());
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(delete("/api/v1/scanner-devices/{deviceId}", deviceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(scannerDeviceRepository.findById(deviceId).orElseThrow().getStatus()).isEqualTo(ScannerDeviceStatus.REVOKED);
    }

    @Test
    void revoke_roselessStranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID deviceId = UUID.randomUUID();
        ScannerDevice device = ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("Gate 1")
                .status(ScannerDeviceStatus.ACTIVE).createdBy("test").build();
        ScannerDevice saved = scannerDeviceRepository.save(device);
        createdDeviceIds.add(saved.getId());
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(delete("/api/v1/scanner-devices/{deviceId}", deviceId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void revoke_unknownDevice_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(delete("/api/v1/scanner-devices/{deviceId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    /**
     * Item #4 of the dispatch's key-behaviors list: revoke a device, then
     * confirm its previously-valid credential now fails deviceAuth entirely
     * (401, not 403 — no Authentication is ever set) on all three
     * device-only endpoints. Checked live, not via a token blacklist.
     */
    @Test
    void revokedDevice_credentialStillStructurallyValid_butFailsAllThreeDeviceOnlyEndpoints_with401() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String ownerToken = jwtService.generateAccessToken(owner);
        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();

        mockMvc.perform(delete("/api/v1/scanner-devices/{deviceId}", deviceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // The revoked device's OWN credential (still cryptographically valid) must now 401 everywhere.
        mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset", deviceId)
                        .header("Authorization", "Bearer " + deviceCredential))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/check-in/validate")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"credential\":\"whatever\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + eventId + "\",\"scans\":[{\"rawCredential\":\"x\",\"capturedAt\":\"2026-01-01T00:00:00Z\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /scanner-devices/{deviceId}/dataset ----

    @Test
    void getDataset_ownDevice_returns200_withCredentialHashesNotRawCredentials() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, UUID.randomUUID(), TicketStatus.VALID);
        String deviceCredential = persistActiveDevice(eventId);

        MvcResult result = mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset",
                        scannerDeviceCredentialService.verify(deviceCredential).orElseThrow())
                        .header("Authorization", "Bearer " + deviceCredential))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.tickets.length()").value(1))
                .andExpect(jsonPath("$.tickets[0].ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.tickets[0].status").value("valid"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String actualHash = body.get("tickets").get(0).get("credentialHash").asText();
        assertThat(actualHash).isNotEqualTo(ticket.getCredential());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expected = digest.digest(ticket.getCredential().getBytes(StandardCharsets.UTF_8));
        String expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(expected);
        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    void getDataset_userJwtInstead_returns403() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        String deviceCredential = persistActiveDevice(eventId);
        UUID deviceId = scannerDeviceCredentialService.verify(deviceCredential).orElseThrow();
        User user = persistUser(Role.CUSTOMER);
        String userToken = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset", deviceId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDataset_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Item #2 of the dispatch's key-behaviors list: a device credential must
     * only fetch ITS OWN dataset — the path's deviceId must match the
     * authenticated device, or any active device could fetch any OTHER
     * device's (and therefore any other event's) full dataset.
     */
    @Test
    void getDataset_pathDeviceIdIsADifferentActiveDevice_returns403() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventA = persistEvent(orgId);
        UUID eventB = persistEvent(orgId);
        String deviceACredential = persistActiveDevice(eventA);
        String deviceBCredential = persistActiveDevice(eventB);
        UUID deviceBId = scannerDeviceCredentialService.verify(deviceBCredential).orElseThrow();

        // Device A's own credential, but requesting Device B's dataset path.
        mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset", deviceBId)
                        .header("Authorization", "Bearer " + deviceACredential))
                .andExpect(status().isForbidden());
    }
}
