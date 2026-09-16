package com.junaldadlawan.event_ticketing_api.moderation;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.moderation.repository.ModerationActionRepository;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.AccountStatus;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code /api/v1/admin/moderation-actions}
 * against real Postgres + real signed JWTs + the real SecurityConfig filter
 * chain (BR-ADMIN-002) — mirrors {@code DisputeIntegrationTest}. Proves the
 * {@code hasRole("ADMIN")} matcher genuinely blocks non-admins, and that
 * SUSPEND/REINSTATE/REMOVE against a real Organization/Event/User row
 * actually mutate its persisted status, not just record a log entry.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationActionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ModerationActionRepository moderationActionRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdActionIds = new ArrayList<>();
    private final List<UUID> createdNotificationIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdActionIds) {
            moderationActionRepository.deleteById(id);
        }
        createdActionIds.clear();
        for (UUID id : createdNotificationIds) {
            notificationRepository.deleteById(id);
        }
        createdNotificationIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (UUID id : createdOrgIds) {
            organizationRepository.deleteById(id);
        }
        createdOrgIds.clear();
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    private User adminUser() {
        return User.builder().id(UUID.randomUUID()).email("admin-" + UUID.randomUUID() + "@test.local").role(Role.ADMIN).build();
    }

    private User customerUser() {
        return User.builder().id(UUID.randomUUID()).email("cust-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
    }

    private UUID persistOrganization(OrganizationStatus status, UUID ownerId) {
        Organization organization = Organization.builder().name("Org " + UUID.randomUUID()).status(status).ownerId(ownerId).documents(List.of()).build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistEvent(UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, java.time.temporal.ChronoUnit.DAYS);
        Event event = Event.builder().organizationId(organizationId).title("Moderation Test Event").description("d")
                .category("music").status(status).ticketPrefix("M" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, java.time.temporal.ChronoUnit.HOURS)).timezone("UTC").build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistUser(AccountStatus accountStatus) {
        User user = User.builder().name("Moderation Test User").email("mod-" + UUID.randomUUID() + "@test.local")
                .passwordHash("hash").role(Role.CUSTOMER).accountStatus(accountStatus).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved.getId();
    }

    // ---- HTTP-layer authorization ----

    @Test
    void create_nonAdmin_returns403() throws Exception {
        User customer = customerUser();
        String token = jwtService.generateAccessToken(customer);

        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + UUID.randomUUID() + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + UUID.randomUUID() + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_nonAdmin_returns403() throws Exception {
        User customer = customerUser();
        String token = jwtService.generateAccessToken(customer);

        mockMvc.perform(get("/api/v1/admin/moderation-actions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ---- ORGANIZATION ----

    @Test
    void suspendReinstateOrganization_realStatusMutatesInPostgres() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID orgId = persistOrganization(OrganizationStatus.APPROVED, UUID.randomUUID());

        var suspendResult = mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"ORGANIZATION\",\"targetId\":\"" + orgId + "\",\"action\":\"SUSPEND\",\"reason\":\"fraud reports\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousStatus").value("APPROVED"))
                .andReturn();
        createdActionIds.add(UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(suspendResult.getResponse().getContentAsString()).get("id").asText()));

        assertThat(organizationRepository.findById(orgId).orElseThrow().getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);

        var reinstateResult = mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"ORGANIZATION\",\"targetId\":\"" + orgId + "\",\"action\":\"REINSTATE\",\"reason\":\"resolved\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        createdActionIds.add(UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(reinstateResult.getResponse().getContentAsString()).get("id").asText()));

        assertThat(organizationRepository.findById(orgId).orElseThrow().getStatus()).isEqualTo(OrganizationStatus.APPROVED);
    }

    // ---- EVENT ----

    @Test
    void suspendEvent_realStatusMutatesInPostgres() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID orgId = persistOrganization(OrganizationStatus.APPROVED, UUID.randomUUID());
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);

        var result = mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"EVENT\",\"targetId\":\"" + eventId + "\",\"action\":\"SUSPEND\",\"reason\":\"policy violation\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousStatus").value("PUBLISHED"))
                .andReturn();
        createdActionIds.add(UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText()));

        assertThat(eventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.SUSPENDED);
    }

    @Test
    void removeEvent_delegatesToRealSoftDelete() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID orgId = persistOrganization(OrganizationStatus.APPROVED, admin.getId());
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        var result = mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"EVENT\",\"targetId\":\"" + eventId + "\",\"action\":\"REMOVE\",\"reason\":\"spam listing\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        createdActionIds.add(UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText()));

        assertThat(eventRepository.findById(eventId).orElseThrow().getDeletedAt()).isNotNull();
    }

    // ---- USER ----

    @Test
    void suspendReinstateUser_realAccountStatusMutatesInPostgres() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID targetUserId = persistUser(AccountStatus.ACTIVE);

        var suspendResult = mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + targetUserId + "\",\"action\":\"SUSPEND\",\"reason\":\"abuse reports\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andReturn();
        createdActionIds.add(UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(suspendResult.getResponse().getContentAsString()).get("id").asText()));

        assertThat(userRepository.findById(targetUserId).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        notificationRepository.findByUserIdOrderByCreatedAtDesc(targetUserId).forEach(n -> createdNotificationIds.add(n.getId()));

        var reinstateResult = mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + targetUserId + "\",\"action\":\"REINSTATE\",\"reason\":\"resolved\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        createdActionIds.add(UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(reinstateResult.getResponse().getContentAsString()).get("id").asText()));

        assertThat(userRepository.findById(targetUserId).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        notificationRepository.findByUserIdOrderByCreatedAtDesc(targetUserId).forEach(n -> {
            if (!createdNotificationIds.contains(n.getId())) {
                createdNotificationIds.add(n.getId());
            }
        });
    }

    @Test
    void suspendUserTwice_returns409() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID targetUserId = persistUser(AccountStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + targetUserId + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isConflict());
    }

    // ---- list() ----

    @Test
    void list_admin_filtersByTargetType() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID targetUserId = persistUser(AccountStatus.ACTIVE);
        var result = mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + targetUserId + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        createdActionIds.add(UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText()));
        notificationRepository.findByUserIdOrderByCreatedAtDesc(targetUserId).forEach(n -> createdNotificationIds.add(n.getId()));

        mockMvc.perform(get("/api/v1/admin/moderation-actions").param("targetType", "USER").param("targetId", targetUserId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].targetId").value(targetUserId.toString()));
    }
}
