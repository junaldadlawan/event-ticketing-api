package com.junaldadlawan.event_ticketing_api.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The CORS policy for browser clients on another origin: only the listed origins, the methods and headers the web app uses, no credentials. */
@SpringBootTest(properties = "app.cors.allowed-origins=https://app.example.com, https://admin.example.com")
@AutoConfigureMockMvc
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aPreflightFromAListedOrigin_isAnsweredBeforeAuthentication_forPatchAndIdempotencyKey() throws Exception {
        mockMvc.perform(options("/api/v1/carts/00000000-0000-0000-0000-000000000000/items/00000000-0000-0000-0000-000000000001")
                        .header("Origin", "https://admin.example.com")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("Idempotency-Key")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void anOriginThatIsNotListed_isRefused_evenForAPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/events").header("Origin", "https://evil.example.com").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events").header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRealRequestFromAListedOrigin_getsTheHeader_andTheClientCanReadTheRequestId_evenOnA401() throws Exception {
        mockMvc.perform(get("/api/v1/events").header("Origin", "https://app.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("X-Request-Id")));
        // an error the browser must still be able to read (so the web app can show it)
        mockMvc.perform(get("/api/v1/users/me").header("Origin", "https://app.example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
    }

    @Test
    void aRequestWithoutAnOriginIsNotAffected() throws Exception {
        mockMvc.perform(get("/api/v1/events")).andExpect(status().isOk()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
