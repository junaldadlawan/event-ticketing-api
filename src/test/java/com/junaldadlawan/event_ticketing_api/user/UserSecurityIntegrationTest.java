package com.junaldadlawan.event_ticketing_api.user;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring end to
 * end, since that can't be meaningfully verified from a WebMvcTest slice
 * (see UserControllerTest). Uses in-memory (unpersisted) User objects to
 * sign role-specific tokens via the real JwtService bean — the filter never
 * looks the subject up in the DB, only the target data (the user list)
 * needs a real persisted row.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User persistedUser;

    @BeforeEach
    void setUp() {
        persistedUser = userRepository.save(User.builder()
                .name("Security Test User")
                .email("security-test-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant"))
                .role(Role.CUSTOMER)
                .build());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(persistedUser.getId());
    }

    @Test
    void adminToken_canListUsers() throws Exception {
        String token = jwtService.generateAccessToken(inMemoryUser(Role.ADMIN));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminToken_cannotListUsers() throws Exception {
        String token = jwtService.generateAccessToken(inMemoryUser(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_cannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminToken_listExcludesSoftDeletedUser() throws Exception {
        persistedUser.markDeleted();
        userRepository.save(persistedUser);
        String token = jwtService.generateAccessToken(inMemoryUser(Role.ADMIN));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + persistedUser.getId() + "')]").doesNotExist());
    }

    // ---- GET/PATCH /users/me: any authenticated caller's own profile, not admin-only ----

    @Test
    void getSelf_authenticatedNonAdmin_returnsOwnProfile() throws Exception {
        String token = jwtService.generateAccessToken(persistedUser);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(persistedUser.getId().toString()));
    }

    @Test
    void getSelf_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateSelf_authenticatedNonAdmin_canUpdateOwnName() throws Exception {
        String token = jwtService.generateAccessToken(persistedUser);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Updated Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void updateSelf_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /** The admin-only PATCH /{id} path must still reject a non-admin, even for their OWN id. */
    @Test
    void update_nonAdminToken_cannotUseAdminOnlyIdPath_evenForOwnAccount() throws Exception {
        String token = jwtService.generateAccessToken(persistedUser);

        mockMvc.perform(patch("/api/v1/users/{id}", persistedUser.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Should Not Apply"}
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * The DTO-level proof (UserSelfUpdateRequest has no role field, so
     * Jackson silently drops it) is in UserControllerTest. This closes the
     * loop end to end against a real persisted row: even with "role":"ADMIN"
     * in the request body, updateSelf must leave the caller's role
     * unchanged in the database, not just in a mocked response.
     */
    @Test
    void updateSelf_roleFieldInBody_realDb_roleTrulyUnchanged() throws Exception {
        String token = jwtService.generateAccessToken(persistedUser);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        User reloaded = userRepository.findById(persistedUser.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.CUSTOMER);
    }

    /**
     * Full-stack proof for the ADMIN path against a real Postgres row:
     * UserServiceImplTest/UserControllerTest prove the per-field logic and
     * status mapping with mocks, but neither confirms the change actually
     * lands (and stays scoped to the one field) in the database.
     */
    @Test
    void update_adminToken_singleFieldPatch_persistsOnlyThatFieldInRealDb() throws Exception {
        String adminToken = jwtService.generateAccessToken(inMemoryUser(Role.ADMIN));
        String newEmail = "admin-updated-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(patch("/api/v1/users/{id}", persistedUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"email\":\"" + newEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.name").value("Security Test User"));

        User reloaded = userRepository.findById(persistedUser.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(newEmail);
        assertThat(reloaded.getName()).isEqualTo("Security Test User");
        assertThat(reloaded.getRole()).isEqualTo(Role.CUSTOMER);
    }

    // ---- PATCH /users/me/change-password: any authenticated caller's own password, not admin-only ----

    @Test
    void updateSelfPassword_authenticatedNonAdmin_correctCurrentPassword_returns200() throws Exception {
        String token = jwtService.generateAccessToken(persistedUser);

        mockMvc.perform(patch("/api/v1/users/me/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"irrelevant","newPassword":"newPlainTextPassword"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateSelfPassword_incorrectCurrentPassword_returns400() throws Exception {
        String token = jwtService.generateAccessToken(persistedUser);

        mockMvc.perform(patch("/api/v1/users/me/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"wrongPassword","newPassword":"newPlainTextPassword"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSelfPassword_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/change-password")
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"irrelevant","newPassword":"newPlainTextPassword"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@test.local")
                .role(role)
                .build();
    }
}
