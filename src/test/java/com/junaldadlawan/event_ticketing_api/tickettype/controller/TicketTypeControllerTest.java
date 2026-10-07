package com.junaldadlawan.event_ticketing_api.tickettype.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.SalesStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    // ---- sales status (pause / resume selling) ----

    private org.springframework.test.web.servlet.ResultActions putSalesStatus(Object ticketTypeId, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/ticket-types/{ticketTypeId}/sales-status", ticketTypeId)
                .contentType("application/json")
                .content(body));
    }

    @Test
    void salesStatus_paused_returns200_withSalesPausedTrue() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        TicketType paused = ticketType(ticketTypeId);
        paused.setSalesPaused(true);
        when(ticketTypeService.setSalesStatus(ticketTypeId, SalesStatus.PAUSED)).thenReturn(paused);

        putSalesStatus(ticketTypeId, "{\"status\":\"PAUSED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketTypeId.toString()))
                .andExpect(jsonPath("$.salesPaused").value(true));
    }

    @Test
    void salesStatus_active_returns200_withSalesPausedFalse() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.setSalesStatus(ticketTypeId, SalesStatus.ACTIVE)).thenReturn(ticketType(ticketTypeId));

        putSalesStatus(ticketTypeId, "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesPaused").value(false));
    }

    @Test
    void salesStatus_missingUnknownOrNullStatus_returns400() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();

        putSalesStatus(ticketTypeId, "{}").andExpect(status().isBadRequest());
        putSalesStatus(ticketTypeId, "{\"status\":null}").andExpect(status().isBadRequest());
        putSalesStatus(ticketTypeId, "{\"status\":\"SOLD_OUT\"}").andExpect(status().isBadRequest());
        putSalesStatus(ticketTypeId, "{\"status\":\"paused\"}").andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/ticket-types/{ticketTypeId}/sales-status", ticketTypeId))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void salesStatus_aMalformedId_returns400() throws Exception {
        putSalesStatus("not-a-uuid", "{\"status\":\"PAUSED\"}").andExpect(status().isBadRequest());
    }

    @Test
    void salesStatus_unknownTicketType_returns404() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.setSalesStatus(ticketTypeId, SalesStatus.PAUSED))
                .thenThrow(new ResourceNotFoundException("Ticket type " + ticketTypeId + " not found"));

        putSalesStatus(ticketTypeId, "{\"status\":\"PAUSED\"}").andExpect(status().isNotFound());
    }

    @Test
    void salesStatus_unauthorizedCaller_returns403() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeService.setSalesStatus(ticketTypeId, SalesStatus.ACTIVE))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type"));

        putSalesStatus(ticketTypeId, "{\"status\":\"ACTIVE\"}").andExpect(status().isForbidden());
    }

    @Test
    void theOldPauseAndResumeUrls_doNotExist() throws Exception {
        UUID ticketTypeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ticket-types/{ticketTypeId}/pause", ticketTypeId)).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/v1/ticket-types/{ticketTypeId}/resume", ticketTypeId)).andExpect(status().is4xxClientError());
    }

    // ---- position / reorder ----

    @Test
    void response_carriesThePositionOfEveryTicketType() throws Exception {
        UUID eventId = UUID.randomUUID();
        TicketType first = ticketType(UUID.randomUUID());
        first.setPosition(0);
        TicketType second = ticketType(UUID.randomUUID());
        second.setPosition(1);
        when(ticketTypeService.list(eventId)).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].position").value(1));
    }

    @Test
    void reorder_returns200_withTheTicketTypesInTheNewOrder() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TicketType first = ticketType(b);
        first.setPosition(0);
        TicketType second = ticketType(a);
        second.setPosition(1);
        when(ticketTypeService.reorder(eventId, List.of(b, a))).thenReturn(List.of(first, second));

        mockMvc.perform(put("/api/v1/events/{eventId}/ticket-types/order", eventId)
                        .contentType("application/json")
                        .content("{\"ticketTypeIds\":[\"" + b + "\",\"" + a + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(b.toString()))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].id").value(a.toString()))
                .andExpect(jsonPath("$[1].position").value(1));
    }

    @Test
    void reorder_missingEmptyOrMalformedIds_return400() throws Exception {
        UUID eventId = UUID.randomUUID();
        for (String body : new String[] {"{}", "{\"ticketTypeIds\":null}", "{\"ticketTypeIds\":[]}",
                "{\"ticketTypeIds\":[null]}", "{\"ticketTypeIds\":[\"not-a-uuid\"]}", "{\"ticketTypeIds\":\"abc\"}"}) {
            mockMvc.perform(put("/api/v1/events/{eventId}/ticket-types/order", eventId)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void reorder_aMismatchedList_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        when(ticketTypeService.reorder(eq(eventId), any()))
                .thenThrow(new BadRequestException("ticketTypeIds must list every ticket type of this event exactly once"));

        mockMvc.perform(put("/api/v1/events/{eventId}/ticket-types/order", eventId)
                        .contentType("application/json")
                        .content("{\"ticketTypeIds\":[\"" + a + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reorder_unknownEventAndUnauthorizedCaller_return404And403() throws Exception {
        UUID missing = UUID.randomUUID();
        UUID forbidden = UUID.randomUUID();
        when(ticketTypeService.reorder(eq(missing), any())).thenThrow(new ResourceNotFoundException("Event " + missing + " not found"));
        when(ticketTypeService.reorder(eq(forbidden), any())).thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type"));
        String body = "{\"ticketTypeIds\":[\"" + UUID.randomUUID() + "\"]}";

        mockMvc.perform(put("/api/v1/events/{eventId}/ticket-types/order", missing).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/events/{eventId}/ticket-types/order", forbidden).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

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
