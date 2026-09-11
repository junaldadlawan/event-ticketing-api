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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventTicketTypeController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors
 * {@code EventControllerTest}. Security-filter enforcement (401/403,
 * cross-org rejection, public/draft gating) is exercised separately in the
 * full-stack integration test, since {@code @WebMvcTest(addFilters = false)}
 * never engages the real filter chain and the service is fully mocked here.
 */
@WebMvcTest(EventTicketTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventTicketTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketTypeService ticketTypeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private TicketType ticketType(UUID id, UUID eventId) {
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        return TicketType.builder()
                .id(id)
                .eventId(eventId)
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

    private String validCreateBody() {
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        return """
                {"name":"General Admission","kind":"GENERAL_ADMISSION",
                "price":{"amount":1000,"currency":"USD"},"quantityTotal":100,
                "saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(saleStartAt, saleEndAt);
    }

    // ---- create() ----

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.create(eq(eventId), any())).thenReturn(ticketType(ticketTypeId, eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ticketTypeId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.quantityAvailable").value(100))
                .andExpect(jsonPath("$.maxPerOrder").value(10));
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        // name omitted entirely -> @NotBlank violation -> 400, not 401/500
        // (confirming the GlobalExceptionHandler fix from Phase 1 still holds here).
        UUID eventId = UUID.randomUUID();
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"kind":"GENERAL_ADMISSION","price":{"amount":1000,"currency":"USD"},
                "quantityTotal":100,"saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(saleStartAt, saleEndAt);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankName_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"name":"   ","kind":"GENERAL_ADMISSION","price":{"amount":1000,"currency":"USD"},
                "quantityTotal":100,"saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(saleStartAt, saleEndAt);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /**
     * BR-EVENT-003 / the confirmed deliberate openapi deviation:
     * quantityTotal is required here even though openapi.yaml's
     * TicketTypeCreate only requires [name, kind, price].
     */
    @Test
    void create_missingQuantityTotal_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"name":"General Admission","kind":"GENERAL_ADMISSION","price":{"amount":1000,"currency":"USD"},
                "saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(saleStartAt, saleEndAt);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingSaleWindow_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = """
                {"name":"General Admission","kind":"GENERAL_ADMISSION",
                "price":{"amount":1000,"currency":"USD"},"quantityTotal":100}
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidCurrencyCode_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"name":"General Admission","kind":"GENERAL_ADMISSION","price":{"amount":1000,"currency":"usd"},
                "quantityTotal":100,"saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(saleStartAt, saleEndAt);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_nonExistentEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTypeService.create(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTypeService.create(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type"));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_saleWindowInvalid_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTypeService.create(eq(eventId), any()))
                .thenThrow(new BadRequestException("saleEndAt must be after saleStartAt"));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isBadRequest());
    }

    // ---- list() ----

    @Test
    void list_existingEvent_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTypeService.list(eventId)).thenReturn(List.of(ticketType(UUID.randomUUID(), eventId)));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()));
    }

    @Test
    void list_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTypeService.list(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_draftEventUnauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTypeService.list(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type"));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isForbidden());
    }
}
