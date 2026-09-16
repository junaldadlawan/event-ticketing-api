package com.junaldadlawan.event_ticketing_api.dispute.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeResponse;
import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
import com.junaldadlawan.event_ticketing_api.dispute.service.DisputeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link DisputeController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors {@code
 * OrderRefundControllerTest}. Real authorization and the SecurityConfig
 * filter chain are exercised in the integration test.
 */
@WebMvcTest(DisputeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisputeService disputeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private DisputeResponse response(UUID id, DisputeStatus status) {
        return new DisputeResponse(id, UUID.randomUUID(), null, UUID.randomUUID(), status,
                "reason", null, Instant.now(), null, null);
    }

    // ---- create() ----

    @Test
    void create_validRequest_returns201() throws Exception {
        DisputeResponse response = response(UUID.randomUUID(), DisputeStatus.OPEN);
        when(disputeService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/disputes")
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + response.orderId() + "\",\"reason\":\"not delivered\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void create_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/disputes")
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/disputes")
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + UUID.randomUUID() + "\",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_neitherOrderNorTicket_returns400() throws Exception {
        when(disputeService.create(any())).thenThrow(new BadRequestException("At least one of orderId or ticketId must be provided"));

        mockMvc.perform(post("/api/v1/disputes")
                        .contentType("application/json")
                        .content("{\"reason\":\"reason\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_notBuyerOrOwner_returns403() throws Exception {
        when(disputeService.create(any())).thenThrow(new ForbiddenException("Only the order's buyer or an admin may raise a dispute against it"));

        mockMvc.perform(post("/api/v1/disputes")
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + UUID.randomUUID() + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_unknownOrder_returns404() throws Exception {
        when(disputeService.create(any())).thenThrow(new ResourceNotFoundException("Order not found"));

        mockMvc.perform(post("/api/v1/disputes")
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + UUID.randomUUID() + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- list() ----

    @Test
    void list_returns200_withPageResponseShape() throws Exception {
        Page<DisputeResponse> page = new PageImpl<>(List.of(response(UUID.randomUUID(), DisputeStatus.OPEN)));
        when(disputeService.list(eq(DisputeStatus.OPEN), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/disputes").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_nonAdmin_returns403() throws Exception {
        when(disputeService.list(isNull(), any())).thenThrow(new ForbiddenException("Admin access required"));

        mockMvc.perform(get("/api/v1/disputes"))
                .andExpect(status().isForbidden());
    }

    // ---- get() ----

    @Test
    void get_returns200() throws Exception {
        UUID disputeId = UUID.randomUUID();
        when(disputeService.get(disputeId)).thenReturn(response(disputeId, DisputeStatus.OPEN));

        mockMvc.perform(get("/api/v1/disputes/{disputeId}", disputeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(disputeId.toString()));
    }

    @Test
    void get_notRaiserOrAdmin_returns403() throws Exception {
        UUID disputeId = UUID.randomUUID();
        when(disputeService.get(disputeId)).thenThrow(new ForbiddenException("Only the user who raised this dispute or an admin may view it"));

        mockMvc.perform(get("/api/v1/disputes/{disputeId}", disputeId))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unknownDispute_returns404() throws Exception {
        UUID disputeId = UUID.randomUUID();
        when(disputeService.get(disputeId)).thenThrow(new ResourceNotFoundException("Dispute not found"));

        mockMvc.perform(get("/api/v1/disputes/{disputeId}", disputeId))
                .andExpect(status().isNotFound());
    }

    // ---- update() ----

    @Test
    void update_validRequest_returns200() throws Exception {
        UUID disputeId = UUID.randomUUID();
        when(disputeService.update(eq(disputeId), any())).thenReturn(response(disputeId, DisputeStatus.RESOLVED));

        mockMvc.perform(patch("/api/v1/disputes/{disputeId}", disputeId)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\",\"resolution\":\"refund issued\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void update_nonAdmin_returns403() throws Exception {
        UUID disputeId = UUID.randomUUID();
        when(disputeService.update(eq(disputeId), any())).thenThrow(new ForbiddenException("Admin access required"));

        mockMvc.perform(patch("/api/v1/disputes/{disputeId}", disputeId)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_alreadyResolved_returns409() throws Exception {
        UUID disputeId = UUID.randomUUID();
        when(disputeService.update(eq(disputeId), any())).thenThrow(new ConflictException("This dispute has already been resolved"));

        mockMvc.perform(patch("/api/v1/disputes/{disputeId}", disputeId)
                        .contentType("application/json")
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_resolutionTooLong_returns400() throws Exception {
        String tooLong = "a".repeat(1001);
        mockMvc.perform(patch("/api/v1/disputes/{disputeId}", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"resolution\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
