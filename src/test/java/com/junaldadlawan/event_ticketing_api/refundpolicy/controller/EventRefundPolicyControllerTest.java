package com.junaldadlawan.event_ticketing_api.refundpolicy.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyResponse;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import com.junaldadlawan.event_ticketing_api.refundpolicy.service.RefundPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventRefundPolicyController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors {@code
 * EventResalePolicyControllerTest}. Security-filter enforcement (this
 * controller's GET is NOT public, unlike resale-policy's) is exercised
 * separately in the full-stack integration test.
 */
@WebMvcTest(EventRefundPolicyController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventRefundPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundPolicyService refundPolicyService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void get_noPolicyYet_returns200_withNoRefundsDefault() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(refundPolicyService.get(eventId)).thenReturn(RefundPolicyResponse.defaultFor(eventId));

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.ruleType").value("NO_REFUNDS"))
                .andExpect(jsonPath("$.daysBeforeEvent").doesNotExist());
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(refundPolicyService.get(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(refundPolicyService.get(eventId))
                .thenThrow(new ForbiddenException("Only the event's organizer/owner, an admin, or a buyer with an order on this event may view its refund policy"));

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_validRequest_returns200_withUpdatedPolicy() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(refundPolicyService.update(eq(eventId), any()))
                .thenReturn(new RefundPolicyResponse(eventId, RefundRuleType.REFUNDABLE_UNTIL_N_DAYS, 14, null, null, null, null, null));

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"REFUNDABLE_UNTIL_N_DAYS\",\"daysBeforeEvent\":14}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleType").value("REFUNDABLE_UNTIL_N_DAYS"))
                .andExpect(jsonPath("$.daysBeforeEvent").value(14));
    }

    @Test
    void update_missingRuleType_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(refundPolicyService.update(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's refund policy"));

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"NO_REFUNDS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(refundPolicyService.update(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"NO_REFUNDS\"}"))
                .andExpect(status().isNotFound());
    }
}
