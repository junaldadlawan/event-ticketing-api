package com.junaldadlawan.event_ticketing_api.resalepolicy.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyResponse;
import com.junaldadlawan.event_ticketing_api.resalepolicy.enums.PriceCapRule;
import com.junaldadlawan.event_ticketing_api.resalepolicy.service.ResalePolicyService;
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
 * Slice test for {@link EventResalePolicyController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors {@code
 * EventTicketTemplateControllerTest}. Security-filter enforcement is
 * exercised separately in the full-stack integration test.
 */
@WebMvcTest(EventResalePolicyController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventResalePolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResalePolicyService resalePolicyService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void get_noPolicyYet_returns200_withDisabledDefault() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(resalePolicyService.get(eventId)).thenReturn(ResalePolicyResponse.defaultFor(eventId));

        mockMvc.perform(get("/api/v1/events/{eventId}/resale-policy", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.priceCapRule").doesNotExist());
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(resalePolicyService.get(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/resale-policy", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_validRequest_returns200_withUpdatedPolicy() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(resalePolicyService.update(eq(eventId), any()))
                .thenReturn(new ResalePolicyResponse(eventId, true, PriceCapRule.FACE_VALUE, null, null, null, null, null));

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"priceCapRule\":\"FACE_VALUE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.priceCapRule").value("FACE_VALUE"));
    }

    @Test
    void update_missingEnabled_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_invalidFeeAmountCurrency_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"priceCapRule\":\"FACE_VALUE_PLUS_FEE\",\"feeAmount\":{\"amount\":100,\"currency\":\"usd\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(resalePolicyService.update(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's resale policy"));

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(resalePolicyService.update(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNotFound());
    }
}
