package com.junaldadlawan.event_ticketing_api.checkin;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
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
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Scanner device locations: set on authorize, moved by PATCH, and snapshotted onto every check-in record. */
@SpringBootTest
@AutoConfigureMockMvc
class ScannerLocationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ScannerDeviceCredentialService deviceCredentials;
    @Autowired private TicketCredentialService ticketCredentials;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ScannerDeviceRepository scannerDeviceRepository;
    @Autowired private CheckInConfigRepository checkInConfigRepository;
    @Autowired private CheckInRecordRepository checkInRecordRepository;
    @Autowired private FallbackScanRecordRepository fallbackScanRecordRepository;
    @Autowired private TicketRepository ticketRepository;

    private final List<UUID> deviceIds = new ArrayList<>();
    private final List<UUID> ticketIds = new ArrayList<>();
    private final List<UUID> memberIds = new ArrayList<>();
    private UUID orgId;
    private UUID eventId;
    private String owner;
    private String organizer;
    private String admin;
    private String stranger;

    @BeforeEach
    void setUp() {
        User ownerUser = user(Role.CUSTOMER);
        User organizerUser = user(Role.CUSTOMER);
        orgId = organizationRepository.save(Organization.builder().name("Location Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        grant(ownerUser, OrganizationRole.OWNER);
        grant(organizerUser, OrganizationRole.ORGANIZER);
        owner = bearer(ownerUser);
        organizer = bearer(organizerUser);
        admin = bearer(user(Role.ADMIN));
        stranger = bearer(user(Role.CUSTOMER));
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Location Test Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED).ticketPrefix("L" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
    }

    @AfterEach
    void tearDown() {
        ticketIds.forEach(id -> jdbc.update("delete from check_in_records where ticket_id = ?", id));
        jdbc.update("delete from fallback_scan_records where event_id = ?", eventId);
        ticketIds.forEach(ticketRepository::deleteById);
        deviceIds.forEach(id -> scannerDeviceRepository.findById(id).ifPresent(scannerDeviceRepository::delete));
        checkInConfigRepository.findByEventId(eventId).ifPresent(checkInConfigRepository::delete);
        jdbc.update("delete from audit_log_entries where event_id = ?", eventId);
        eventRepository.deleteById(eventId);
        memberIds.forEach(id -> organizationMemberRepository.findByUserIdAndOrganizationId(id, orgId)
                .ifPresent(organizationMemberRepository::delete));
        organizationRepository.deleteById(orgId);
    }

    @Test
    void authorize_savesTheLocation_trimsIt_blankIsNone_andTooLongIsRefused() throws Exception {
        authorize("Front door", "  Gate A  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.location").value("Gate A"))
                .andExpect(jsonPath("$.credential").isNotEmpty());
        authorize("Side door", "   ").andExpect(status().isCreated()).andExpect(jsonPath("$.location").doesNotExist());
        authorize("Back door", null).andExpect(status().isCreated()).andExpect(jsonPath("$.location").doesNotExist());
        // validation errors are 400 in this API (GlobalExceptionHandler)
        authorize("Roof", "x".repeat(101)).andExpect(status().isBadRequest());
    }

    @Test
    void theDeviceItself_theOwner_anOrganizer_andAnAdmin_mayMoveIt_butNobodyElse() throws Exception {
        UUID deviceId = device("Gate A", ScannerDeviceStatus.ACTIVE);
        String deviceToken = "Bearer " + deviceCredentials.generate(deviceId);
        String otherDevice = "Bearer " + deviceCredentials.generate(device(null, ScannerDeviceStatus.ACTIVE));

        move(deviceToken, deviceId, "Gate B").andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Gate B"))
                .andExpect(jsonPath("$.credential").doesNotExist());   // never shown again
        move(owner, deviceId, "Gate C").andExpect(status().isOk()).andExpect(jsonPath("$.location").value("Gate C"));
        move(organizer, deviceId, "Gate D").andExpect(status().isOk());
        move(admin, deviceId, " ").andExpect(status().isOk()).andExpect(jsonPath("$.location").doesNotExist());

        move(otherDevice, deviceId, "Gate X").andExpect(status().isForbidden());
        move(stranger, deviceId, "Gate X").andExpect(status().isForbidden());
        move(owner, UUID.randomUUID(), "Gate X").andExpect(status().isNotFound());
        move(owner, device("Gate A", ScannerDeviceStatus.REVOKED), "Gate B").andExpect(status().isConflict());

        String logged = jdbc.queryForObject("select details::text from audit_log_entries where action = 'scanner_device.location_changed'"
                + " and target_id = ? order by created_at limit 1", String.class, deviceId);
        assertThat(logged).contains("\"from\": \"Gate A\"").contains("\"to\": \"Gate B\"");
    }

    @Test
    void eachScanKeepsTheLocationItWasMadeAt_evenAfterTheDeviceMoves() throws Exception {
        UUID deviceId = device("Gate A", ScannerDeviceStatus.ACTIVE);
        String deviceToken = "Bearer " + deviceCredentials.generate(deviceId);
        Ticket online = ticket();

        mockMvc.perform(post("/api/v1/check-in/validate").header("Authorization", deviceToken).contentType("application/json")
                        .content("{\"credential\":\"" + online.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannerLocation").value("Gate A"));
        move(owner, deviceId, "Gate B").andExpect(status().isOk());
        assertThat(locationsOf(online)).containsExactly("Gate A");

        // offline scans: the location sent with a scan wins, else the device's current one
        checkInConfigRepository.save(CheckInConfig.builder().eventId(eventId).mode(CheckInMode.PURE_OFFLINE).build());
        Ticket withOwnLocation = ticket();
        Ticket withoutLocation = ticket();
        mockMvc.perform(post("/api/v1/check-in/fallback-scans").header("Authorization", deviceToken).contentType("application/json")
                        .content("{\"eventId\":\"" + eventId + "\",\"scans\":["
                                + "{\"rawCredential\":\"" + withOwnLocation.getCredential() + "\",\"capturedAt\":\"" + Instant.now() + "\",\"location\":\"Gate Z\"},"
                                + "{\"rawCredential\":\"" + withoutLocation.getCredential() + "\",\"capturedAt\":\"" + Instant.now() + "\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scannerLocation").value("Gate Z"))
                .andExpect(jsonPath("$[1].scannerLocation").value("Gate B"));
        assertThat(locationsOf(withOwnLocation)).containsExactly("Gate Z");
        assertThat(locationsOf(withoutLocation)).containsExactly("Gate B");
    }

    // ---- helpers ----

    private ResultActions authorize(String label, String location) throws Exception {
        String body = "{\"deviceLabel\":\"" + label + "\"" + (location == null ? "" : ",\"location\":\"" + location + "\"") + "}";
        ResultActions result = mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                .header("Authorization", owner).contentType("application/json").content(body));
        String response = result.andReturn().getResponse().getContentAsString();
        if (result.andReturn().getResponse().getStatus() == 201) {
            deviceIds.add(UUID.fromString(objectMapper.readTree(response).get("id").asText()));
        }
        return result;
    }

    private ResultActions move(String caller, UUID deviceId, String location) throws Exception {
        return mockMvc.perform(patch("/api/v1/scanner-devices/{deviceId}", deviceId).header("Authorization", caller)
                .contentType("application/json").content("{\"location\":\"" + location + "\"}"));
    }

    private List<String> locationsOf(Ticket ticket) {
        return checkInRecordRepository.findAll().stream().filter(r -> r.getTicketId().equals(ticket.getId()))
                .map(CheckInRecord::getScannerLocation).toList();
    }

    private UUID device(String location, ScannerDeviceStatus status) {
        UUID id = UUID.randomUUID();
        scannerDeviceRepository.save(ScannerDevice.builder().id(id).eventId(eventId).deviceLabel("Test Device")
                .location(location).status(status).createdBy("test").build());
        deviceIds.add(id);
        return id;
    }

    private Ticket ticket() {
        UUID id = UUID.randomUUID();
        Ticket saved = ticketRepository.save(Ticket.builder().id(id).orderId(UUID.randomUUID()).eventId(eventId)
                .ticketTypeId(UUID.randomUUID()).ownerId(UUID.randomUUID())
                .ticketNumber("LOC-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential(ticketCredentials.generate(id, 0)).credentialVersion(0).status(TicketStatus.VALID).build());
        ticketIds.add(id);
        return saved;
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("loc-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private void grant(User user, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(user.getId()).organizationId(orgId).build();
        member.getRoles().add(role);
        organizationMemberRepository.save(member);
        memberIds.add(user.getId());
    }
}
