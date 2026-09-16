package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.PlatformAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.AnalyticsService;
import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link PlatformAnalyticsController}'s own behavior - real
 * SecurityConfig {@code hasRole("ADMIN")} enforcement is exercised in
 * {@code AnalyticsIntegrationTest}.
 */
@WebMvcTest(PlatformAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlatformAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Test
    void get_validRequest_returns200() throws Exception {
        when(analyticsService.getPlatformAnalytics()).thenReturn(
                new PlatformAnalyticsResponse(new MoneyDto(1000000L, "USD"), 12L, 45L));

        mockMvc.perform(get("/api/v1/analytics/platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGmv.amount").value(1000000))
                .andExpect(jsonPath("$.activeOrganizers").value(12))
                .andExpect(jsonPath("$.eventVolume").value(45));
    }

    @Test
    void get_nonAdmin_returns403() throws Exception {
        when(analyticsService.getPlatformAnalytics()).thenThrow(new ForbiddenException("Admin access required"));

        mockMvc.perform(get("/api/v1/analytics/platform"))
                .andExpect(status().isForbidden());
    }
}
