package com.junaldadlawan.event_ticketing_api.checkin;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code GET/PATCH
 * /events/{eventId}/check-in-config} against real Postgres + real signed
 * JWTs/device credentials. Proves item #6 of the dispatch's key-behaviors
 * list: GET is reachable by BOTH a user JWT and a device credential (not
 * device-only, unlike the other three check-in endpoints), while PATCH stays
 * owner/organizer/admin-only. Also proves the mode-switch warning's two
 * independently-required conditions end-to-end (BR-CHECKIN item #8).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckInConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ScannerDeviceCredentialService scannerDeviceCredentialService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CheckInConfigRepository checkInConfigRepository;
    @Autowired
    private ScannerDeviceRepository scannerDeviceRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdConfigIds = new ArrayList<>();
    private final List<UUID> createdDeviceIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdConfigIds) {
            checkInConfigRepository.deleteById(id);
        }
        createdConfigIds.clear();
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
        User user = User.builder().name("CheckInConfig Test User").email("checkin-config-" + UUID.randomUUID() + "@test.local")
                .passwordHash("irrelevant").role(role).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder().name("CheckInConfig Test Org " + UUID.randomUUID())
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
        Event event = Event.builder().organizationId(organizationId).title("CheckInConfig Test Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED)
                .ticketPrefix("C" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private String persistActiveDevice(UUID eventId) {
        UUID deviceId = UUID.randomUUID();
        ScannerDevice device = ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("Test Gate")
                .status(ScannerDeviceStatus.ACTIVE).createdBy("test").build();
        ScannerDevice saved = scannerDeviceRepository.save(device);
        createdDeviceIds.add(saved.getId());
        return scannerDeviceCredentialService.generate(deviceId);
    }

    private void trackConfigForEvent(UUID eventId) {
        checkInConfigRepository.findByEventId(eventId).ifPresent(c -> createdConfigIds.add(c.getId()));
    }

    // ---- GET — any authenticated caller, user OR device ----

    @Test
    void get_noConfigYet_returns200_standardDefault() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User user = persistUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.mode").value("STANDARD"))
                .andExpect(jsonPath("$.offlineFallbackExpirySeconds").value(300));
    }

    @Test
    void get_withUserJwt_returns200_notForbidden() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User user = persistUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void get_withDeviceCredential_returns200() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        String deviceCredential = persistActiveDevice(eventId);

        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + deviceCredential))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()));
    }

    @Test
    void get_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        User user = persistUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---- PATCH — owner/organizer/admin only ----

    @Test
    void update_owner_returns200_persistsMode() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"PURE_OFFLINE\",\"offlineFallbackExpirySeconds\":600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PURE_OFFLINE"))
                .andExpect(jsonPath("$.offlineFallbackExpirySeconds").value(600))
                .andExpect(jsonPath("$.warning").doesNotExist());
        trackConfigForEvent(eventId);

        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PURE_OFFLINE"))
                .andExpect(jsonPath("$.offlineFallbackExpirySeconds").value(600));
    }

    @Test
    void update_organizer_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        String organizerToken = jwtService.generateAccessToken(organizer);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isOk());
        trackConfigForEvent(eventId);
    }

    @Test
    void update_admin_returns200_withNoOrganizationMembershipAtAll() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isOk());
        trackConfigForEvent(eventId);
    }

    @Test
    void update_roselessStranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"PURE_OFFLINE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_deviceCredential_returns403_deviceAuthCannotPatch() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        String deviceCredential = persistActiveDevice(eventId);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + deviceCredential)
                        .contentType("application/json")
                        .content("{\"mode\":\"PURE_OFFLINE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- Mode-switch warning — the two independently-required conditions, end-to-end ----

    @Test
    void update_modeChangesWithActiveDevice_returnsWarning() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        persistActiveDevice(eventId); // an active device already exists for this event
        String ownerToken = jwtService.generateAccessToken(owner);

        // Establish the initial STANDARD config first.
        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").doesNotExist());
        trackConfigForEvent(eventId);

        // Now actually switch mode while the device is still active -> warning.
        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"PURE_OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PURE_OFFLINE"))
                .andExpect(jsonPath("$.warning").isNotEmpty());
    }

    @Test
    void update_modeChangesWithZeroActiveDevices_noWarning() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isOk());
        trackConfigForEvent(eventId);

        // No devices exist at all for this event -> switching mode still gets no warning.
        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"PURE_OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    void update_sameModeAsBefore_activeDeviceExists_noWarning() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        persistActiveDevice(eventId);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isOk());
        trackConfigForEvent(eventId);

        // PATCHing with the SAME mode (just the expiry) while a device exists -> still no warning.
        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\",\"offlineFallbackExpirySeconds\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offlineFallbackExpirySeconds").value(120))
                .andExpect(jsonPath("$.warning").doesNotExist());
    }
}
