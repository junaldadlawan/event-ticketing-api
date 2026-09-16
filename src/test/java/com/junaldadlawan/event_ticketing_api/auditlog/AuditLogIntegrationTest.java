package com.junaldadlawan.event_ticketing_api.auditlog;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code GET /api/v1/audit-log}
 * against real Postgres + real signed JWTs + the real SecurityConfig filter
 * chain (BR-NFR-005) - mirrors {@code ModerationActionIntegrationTest}'s
 * HTTP-layer-authorization section. The actual per-trigger-point writes
 * (refund issuance, role assignment, event cancellation, dispute
 * resolution, account suspension) are proven in each of their own existing
 * integration tests, not duplicated here - this class only proves the
 * read-side admin gate and the {@code actorId} filter against real rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;
    @Autowired
    private UserRepository userRepository;

    private final List<UUID> createdEntryIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdEntryIds) {
            auditLogEntryRepository.deleteById(id);
        }
        createdEntryIds.clear();
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    private User adminUser() {
        return User.builder().id(UUID.randomUUID()).email("audit-admin-" + UUID.randomUUID() + "@test.local").role(Role.ADMIN).build();
    }

    private User customerUser() {
        return User.builder().id(UUID.randomUUID()).email("audit-cust-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
    }

    private UUID persistEntry(UUID actorId, String action) {
        AuditLogEntry saved = auditLogEntryRepository.save(
                AuditLogEntry.builder().actorId(actorId).action(action).targetType("Refund").targetId(UUID.randomUUID()).build());
        createdEntryIds.add(saved.getId());
        return saved.getId();
    }

    @Test
    void list_admin_returns200_withRealRow() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID actorId = UUID.randomUUID();
        persistEntry(actorId, "refund.issued");

        mockMvc.perform(get("/api/v1/audit-log").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.actorId=='" + actorId + "')]").exists());
    }

    @Test
    void list_admin_filtersByActorId() throws Exception {
        User admin = adminUser();
        String token = jwtService.generateAccessToken(admin);
        UUID actorIdA = UUID.randomUUID();
        UUID actorIdB = UUID.randomUUID();
        persistEntry(actorIdA, "event.cancelled");
        persistEntry(actorIdB, "moderation.suspend");

        mockMvc.perform(get("/api/v1/audit-log").param("actorId", actorIdA.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.actorId=='" + actorIdA + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.actorId=='" + actorIdB + "')]").doesNotExist());
    }

    @Test
    void list_nonAdmin_returns403() throws Exception {
        User customer = customerUser();
        String token = jwtService.generateAccessToken(customer);

        mockMvc.perform(get("/api/v1/audit-log").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/audit-log"))
                .andExpect(status().isUnauthorized());
    }
}
