package com.junaldadlawan.event_ticketing_api.payout.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.payout.service.PayoutService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link OrganizationPayoutController} — mirrors {@code
 * EventResaleListingControllerTest}'s paginated-listing style. Falls under
 * the pre-existing {@code /api/v1/organizations/**} matcher, so security
 * enforcement (owner/organizer/admin only) is proven in the integration
 * test, not here.
 */
@WebMvcTest(OrganizationPayoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrganizationPayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayoutService payoutService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void list_existingOrg_returns200_pagedShape() throws Exception {
        UUID orgId = UUID.randomUUID();
        PayoutResponse payout = new PayoutResponse(UUID.randomUUID(), orgId, new MoneyDto(10_000L, "USD"),
                new MoneyDto(500L, "USD"), new MoneyDto(9_500L, "USD"), LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31), PayoutStatus.PAID, Instant.now(), null);
        when(payoutService.list(eq(orgId), any()))
                .thenReturn(new PageImpl<>(List.of(payout), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/payouts", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].organizationId").value(orgId.toString()))
                .andExpect(jsonPath("$.content[0].net.amount").value(9500))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_emptyResult_returns200_emptyContent() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(payoutService.list(eq(orgId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/payouts", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_unknownOrg_returns404() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(payoutService.list(eq(orgId), any()))
                .thenThrow(new ResourceNotFoundException("Organization " + orgId + " not found"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/payouts", orgId))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_unauthorizedCaller_returns403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(payoutService.list(eq(orgId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may view its payouts"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/payouts", orgId))
                .andExpect(status().isForbidden());
    }
}
