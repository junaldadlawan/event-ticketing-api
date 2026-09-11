package com.junaldadlawan.event_ticketing_api.tickettype.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link TicketTypeController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors
 * {@code EventControllerTest}. Security-filter enforcement (401/403,
 * cross-org rejection, public/draft gating) is exercised separately in the
 * full-stack integration test.
 */
@WebMvcTest(TicketTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketTypeService ticketTypeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private TicketType ticketType(UUID id) {
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        return TicketType.builder()
                .id(id)
                .eventId(UUID.randomUUID())
                .name("General Admission")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(100)
                .quantityAvailable(100)
                .saleStartAt(saleStartAt)
                .saleEndAt(saleEndAt)
                .maxPerOrder(10)
                .build();
    }

    // ---- get() ----

    @Test
    void get_existingTicketType_returns200() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.get(ticketTypeId)).thenReturn(ticketType(ticketTypeId));

        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketTypeId.toString()))
                .andExpect(jsonPath("$.name").value("General Admission"));
    }

    @Test
    void get_unknownTicketType_returns404() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.get(ticketTypeId)).thenThrow(new ResourceNotFoundException("Ticket type " + ticketTypeId + " not found"));

        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_draftEventUnauthorizedCaller_returns403() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.get(ticketTypeId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type"));

        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId))
                .andExpect(status().isForbidden());
    }

    // ---- update() ----

    @Test
    void update_validRequest_returns200() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        TicketType updated = ticketType(ticketTypeId);
        updated.setName("Renamed");
        when(ticketTypeService.update(eq(ticketTypeId), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Renamed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void update_blankName_returns400() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.update(eq(ticketTypeId), any()))
                .thenThrow(new BadRequestException("Ticket type name must not be blank"));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .contentType("application/json")
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_negativeQuantityTotal_returns400() throws Exception {
        // @Positive constraint violation -> 400 via bean validation, not 401/500.
        UUID ticketTypeId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .contentType("application/json")
                        .content("""
                                {"quantityTotal":-5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_saleWindowInvalid_returns400() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.update(eq(ticketTypeId), any()))
                .thenThrow(new BadRequestException("saleEndAt must be after saleStartAt"));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .contentType("application/json")
                        .content("""
                                {"saleEndAt":"2020-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_crossOrgCaller_returns403() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.update(eq(ticketTypeId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type"));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Hijacked"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unknownTicketType_returns404() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.update(eq(ticketTypeId), any()))
                .thenThrow(new ResourceNotFoundException("Ticket type " + ticketTypeId + " not found"));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isNotFound());
    }
}
