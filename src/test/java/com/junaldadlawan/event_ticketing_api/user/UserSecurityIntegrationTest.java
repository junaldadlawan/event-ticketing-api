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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

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

    /**
     * Real-concurrency proof for {@code UserServiceImpl#saveOrConflict}:
     * UserServiceImplTest's race test only *simulates* the
     * DataIntegrityViolationException via a mocked saveAndFlush. This drives
     * two real threads against the real Postgres uq_users_email constraint —
     * two DIFFERENT persisted users concurrently PATCHing /users/me to the
     * SAME brand-new email. Exactly one must win (200) and the other must
     * get a real 409 (not a raw 500), and the final DB state must show the
     * email landed on exactly one of the two rows. Same ExecutorService/
     * CountDownLatch idiom as
     * ScannerDeviceIntegrationTest#authorize_pureOfflineMode_concurrentRequests_onlyOneEverActive.
     */
    @Test
    void updateSelf_concurrentRequestsToSameNewEmail_exactlyOneSucceeds() throws Exception {
        User secondUser = userRepository.save(User.builder()
                .name("Second Security Test User")
                .email("security-test-2-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant"))
                .role(Role.CUSTOMER)
                .build());
        try {
            String tokenA = jwtService.generateAccessToken(persistedUser);
            String tokenB = jwtService.generateAccessToken(secondUser);
            String contestedEmail = "contested-" + UUID.randomUUID() + "@example.com";

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Callable<Integer> requestA = () -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(patch("/api/v1/users/me")
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType("application/json")
                                .content("{\"email\":\"" + contestedEmail + "\"}"))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> requestB = () -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(patch("/api/v1/users/me")
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType("application/json")
                                .content("{\"email\":\"" + contestedEmail + "\"}"))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> futureA = executor.submit(requestA);
            Future<Integer> futureB = executor.submit(requestB);
            ready.await();
            go.countDown();

            int statusA = futureA.get();
            int statusB = futureB.get();
            executor.shutdown();

            long successCount = Stream.of(statusA, statusB).filter(s -> s == 200).count();
            long conflictCount = Stream.of(statusA, statusB).filter(s -> s == 409).count();
            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);

            User reloadedA = userRepository.findById(persistedUser.getId()).orElseThrow();
            User reloadedB = userRepository.findById(secondUser.getId()).orElseThrow();
            long emailLandedOn = Stream.of(reloadedA, reloadedB)
                    .filter(u -> contestedEmail.equals(u.getEmail()))
                    .count();
            assertThat(emailLandedOn).isEqualTo(1);
        } finally {
            userRepository.deleteById(secondUser.getId());
        }
    }

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@test.local")
                .role(role)
                .build();
    }
}
