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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@test.local")
                .role(role)
                .build();
    }
}
