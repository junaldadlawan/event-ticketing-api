package com.junaldadlawan.event_ticketing_api.platformfee.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.EffectivePlatformFeeResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
import com.junaldadlawan.event_ticketing_api.platformfee.service.PlatformFeeService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlatformFeeController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlatformFeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformFeeService platformFeeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private PlatformFeeRuleResponse percentageRule(FeeScope scope, UUID scopeId, String percent) {
        return new PlatformFeeRuleResponse(UUID.randomUUID(), scope, scopeId, FeeType.PERCENTAGE, new BigDecimal(percent), null,
                Instant.now(), Instant.now());
    }

    // ---- PUT ----

    @Test
    void setDefault_returns200_withTheRule_andPassesTheRequest() throws Exception {
        when(platformFeeService.upsert(eq(FeeScope.PLATFORM), eq(null), any())).thenReturn(percentageRule(FeeScope.PLATFORM, null, "7.5"));

        mockMvc.perform(put("/api/v1/platform-fees/default").contentType("application/json")
                        .content("{\"type\":\"PERCENTAGE\",\"percentage\":7.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PLATFORM"))
                .andExpect(jsonPath("$.scopeId").isEmpty())
                .andExpect(jsonPath("$.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.percentage").value(7.5));

        verify(platformFeeService).upsert(eq(FeeScope.PLATFORM), eq(null),
                argThat(r -> r.type() == FeeType.PERCENTAGE && r.percentage().compareTo(new BigDecimal("7.5")) == 0 && r.flatAmount() == null));
    }

    @Test
    void setForOrganizationAndEvent_useTheirScopes_andAcceptAFlatFee() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PlatformFeeRuleResponse flat = new PlatformFeeRuleResponse(UUID.randomUUID(), FeeScope.ORGANIZATION, orgId, FeeType.FLAT, null,
                new MoneyDto(250, "USD"), Instant.now(), Instant.now());
        when(platformFeeService.upsert(eq(FeeScope.ORGANIZATION), eq(orgId), any())).thenReturn(flat);
        when(platformFeeService.upsert(eq(FeeScope.EVENT), eq(eventId), any())).thenReturn(percentageRule(FeeScope.EVENT, eventId, "0"));

        mockMvc.perform(put("/api/v1/platform-fees/organizations/{id}", orgId).contentType("application/json")
                        .content("{\"type\":\"FLAT\",\"flatAmount\":{\"amount\":250,\"currency\":\"USD\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flatAmount.amount").value(250))
                .andExpect(jsonPath("$.flatAmount.currency").value("USD"));
        mockMvc.perform(put("/api/v1/platform-fees/events/{id}", eventId).contentType("application/json")
                        .content("{\"type\":\"PERCENTAGE\",\"percentage\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeId").value(eventId.toString()));
    }

    @Test
    void put_invalidBodies_return400() throws Exception {
        for (String body : new String[] {
                "{}",
                "{\"percentage\":5}",
                "{\"type\":\"BANANA\",\"percentage\":5}",
                "{\"type\":\"PERCENTAGE\",\"percentage\":-1}",
                "{\"type\":\"PERCENTAGE\",\"percentage\":100.01}",
                "{\"type\":\"PERCENTAGE\",\"percentage\":5.123}",
                "{\"type\":\"PERCENTAGE\",\"percentage\":\"lots\"}",
                "{\"type\":\"FLAT\",\"flatAmount\":{\"amount\":-5,\"currency\":\"USD\"}}",
                "{\"type\":\"FLAT\",\"flatAmount\":{\"amount\":5,\"currency\":\"usd\"}}",
                "{\"type\":\"FLAT\",\"flatAmount\":{\"amount\":5}}"}) {
            mockMvc.perform(put("/api/v1/platform-fees/default").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void put_atTheLimits_isAccepted() throws Exception {
        when(platformFeeService.upsert(any(), any(), any())).thenReturn(percentageRule(FeeScope.PLATFORM, null, "100"));

        mockMvc.perform(put("/api/v1/platform-fees/default").contentType("application/json")
                        .content("{\"type\":\"PERCENTAGE\",\"percentage\":100}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform-fees/default").contentType("application/json")
                        .content("{\"type\":\"PERCENTAGE\",\"percentage\":0.01}")).andExpect(status().isOk());
    }

    @Test
    void put_serviceErrors_mapToTheirStatuses() throws Exception {
        UUID missing = UUID.randomUUID();
        when(platformFeeService.upsert(eq(FeeScope.ORGANIZATION), eq(missing), any()))
                .thenThrow(new ResourceNotFoundException("Organization " + missing + " not found"));
        when(platformFeeService.upsert(eq(FeeScope.PLATFORM), eq(null), any()))
                .thenThrow(new ForbiddenException("Admin access required"))
                .thenThrow(new BadRequestException("percentage is required for a PERCENTAGE fee"));
        String body = "{\"type\":\"PERCENTAGE\",\"percentage\":5}";

        mockMvc.perform(put("/api/v1/platform-fees/organizations/{id}", missing).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/platform-fees/default").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/platform-fees/default").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/platform-fees/events/{id}", "not-a-uuid").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    // ---- DELETE ----

    @Test
    void delete_returns204_forEachScope() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/platform-fees/default")).andExpect(status().isNoContent()).andExpect(content().string(""));
        mockMvc.perform(delete("/api/v1/platform-fees/organizations/{id}", orgId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/platform-fees/events/{id}", eventId)).andExpect(status().isNoContent());

        verify(platformFeeService).delete(FeeScope.PLATFORM, null);
        verify(platformFeeService).delete(FeeScope.ORGANIZATION, orgId);
        verify(platformFeeService).delete(FeeScope.EVENT, eventId);
    }

    @Test
    void delete_noRule_returns404_andNonAdmin403() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("No platform fee rule")).when(platformFeeService).delete(FeeScope.ORGANIZATION, orgId);
        doThrow(new ForbiddenException("Admin access required")).when(platformFeeService).delete(FeeScope.EVENT, eventId);

        mockMvc.perform(delete("/api/v1/platform-fees/organizations/{id}", orgId)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/platform-fees/events/{id}", eventId)).andExpect(status().isForbidden());
    }

    // ---- GET ----

    @Test
    void list_returnsTheRules_andPassesTheScopeFilter() throws Exception {
        when(platformFeeService.list(null)).thenReturn(List.of(percentageRule(FeeScope.PLATFORM, null, "5")));
        when(platformFeeService.list(FeeScope.EVENT)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/platform-fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scope").value("PLATFORM"));
        mockMvc.perform(get("/api/v1/platform-fees").param("scope", "EVENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/platform-fees").param("scope", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void effective_returnsTheRuleThatApplies_orANullRule() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(platformFeeService.effective(eventId)).thenReturn(new EffectivePlatformFeeResponse(eventId, orgId, null));

        mockMvc.perform(get("/api/v1/platform-fees/effective").param("eventId", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.organizationId").value(orgId.toString()))
                .andExpect(jsonPath("$.rule").isEmpty());
        mockMvc.perform(get("/api/v1/platform-fees/effective")).andExpect(status().isBadRequest());
    }
}
