package com.junaldadlawan.event_ticketing_api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-NFR-004: login is rate-limited per caller IP. A low limit is set for this test's own context so the cap is reached in a
 * few calls (the suite's default is higher). Wrong-credential logins still count - the limiter runs before the credential check -
 * so a brute-force run is capped regardless of whether any attempt would have succeeded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ratelimit.login-per-minute=3")
class LoginRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void repeatedLoginAttemptsFromOneClientAreCappedWith429() throws Exception {
        String body = "{\"email\":\"nobody@demo.test\",\"password\":\"whatever\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());   // within the limit: normal invalid-credentials 401
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests());    // 4th in the window: capped
    }
}
