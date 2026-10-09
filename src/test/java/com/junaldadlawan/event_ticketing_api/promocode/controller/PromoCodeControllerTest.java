package com.junaldadlawan.event_ticketing_api.promocode.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.enums.PromoCodeStatus;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link PromoCodeController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors
 * {@code EventTicketTypeControllerTest}. Security-filter enforcement
 * (401/403, cross-org rejection) is exercised separately in
 * {@code EventPromoCodeAccessIntegrationTest}.
 */
@WebMvcTest(PromoCodeController.class)
@AutoConfigureMockMvc(addFilters = false)
class PromoCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromoCodeService promoCodeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private PromoCode promoCode(UUID id, UUID eventId) {
        return PromoCode.builder()
                .id(id)
                .eventId(eventId)
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .applicableTicketTypeIds(Set.of())
                .validFrom(Instant.now())
                .validUntil(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();
    }

    private String createBody() {
        return """
                {"code":"SAVE10","discountType":"PERCENTAGE","discountValue":10,
                "validFrom":"%s","validUntil":"%s"}
                """.formatted(Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS));
    }

    // ---- create() ----

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID promoCodeId = UUID.randomUUID();
        when(promoCodeService.create(eq(eventId), any())).thenReturn(promoCode(promoCodeId, eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(promoCodeId.toString()))
                .andExpect(jsonPath("$.code").value("SAVE10"));
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content("""
                                {"discountType":"PERCENTAGE","discountValue":10}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankCode_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content("""
                                {"code":"   ","discountType":"PERCENTAGE","discountValue":10,
                                "validFrom":"%s","validUntil":"%s"}
                                """.formatted(Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_negativeDiscountValue_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10","discountType":"PERCENTAGE","discountValue":-5,
                                "validFrom":"%s","validUntil":"%s"}
                                """.formatted(Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_validUntilBeforeValidFrom_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.create(eq(eventId), any())).thenThrow(new BadRequestException("validUntil must be after validFrom"));

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content(createBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_stranger_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.create(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's promo codes"));

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content(createBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nonExistentEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.create(eq(eventId), any())).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content(createBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_duplicateCode_returns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.create(eq(eventId), any()))
                .thenThrow(new ConflictException("A promo code with code 'SAVE10' already exists for this event"));

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .contentType("application/json")
                        .content(createBody()))
                .andExpect(status().isConflict());
    }

    // ---- list() ----

    @Test
    void list_existingEvent_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.list(eventId)).thenReturn(List.of(promoCode(UUID.randomUUID(), eventId)));

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void list_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.list(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's promo codes"));

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId))
                .andExpect(status().isForbidden());
    }

    // ---- paused / usedCount in responses, edit, status, delete ----

    @Test
    void list_includesPausedAndUsedCount() throws Exception {
        UUID eventId = UUID.randomUUID();
        PromoCode paused = promoCode(UUID.randomUUID(), eventId);
        paused.setPaused(true);
        PromoCode active = promoCode(UUID.randomUUID(), eventId);
        when(promoCodeService.list(eventId)).thenReturn(List.of(paused, active));
        when(promoCodeService.usedCount(paused)).thenReturn(3);
        when(promoCodeService.usedCount(active)).thenReturn(0);

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paused").value(true))
                .andExpect(jsonPath("$[0].usedCount").value(3))
                .andExpect(jsonPath("$[1].paused").value(false))
                .andExpect(jsonPath("$[1].usedCount").value(0));
    }

    @Test
    void update_returns200_withTheUpdatedCode() throws Exception {
        UUID id = UUID.randomUUID();
        PromoCode updated = promoCode(id, UUID.randomUUID());
        updated.setDiscountValue(BigDecimal.valueOf(25));
        when(promoCodeService.update(eq(id), any())).thenReturn(updated);
        when(promoCodeService.usedCount(updated)).thenReturn(2);

        mockMvc.perform(patch("/api/v1/promo-codes/{id}", id)
                        .contentType("application/json")
                        .content("{\"discountValue\":25,\"usageLimitTotal\":0,\"applicableTicketTypeIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.discountValue").value(25))
                .andExpect(jsonPath("$.usedCount").value(2));
    }

    @Test
    void update_anEmptyBody_isAccepted_everythingIsOptional() throws Exception {
        UUID id = UUID.randomUUID();
        when(promoCodeService.update(eq(id), any())).thenReturn(promoCode(id, UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/promo-codes/{id}", id).contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void update_invalidValues_return400() throws Exception {
        UUID id = UUID.randomUUID();
        for (String body : new String[] {
                "{\"code\":\"" + "X".repeat(51) + "\"}",
                "{\"code\":\"<b>hi</b>\"}",
                "{\"discountValue\":-1}",
                "{\"discountType\":\"BOGO\"}",
                "{\"usageLimitTotal\":-1}",
                "{\"usageLimitPerBuyer\":-5}",
                "{\"validFrom\":\"not-a-date\"}",
                "{\"applicableTicketTypeIds\":[\"not-a-uuid\"]}"}) {
            mockMvc.perform(patch("/api/v1/promo-codes/{id}", id).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void update_conflictsAndNotFoundAndForbidden_mapToTheirStatuses() throws Exception {
        UUID duplicate = UUID.randomUUID();
        UUID used = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID forbidden = UUID.randomUUID();
        UUID badLimit = UUID.randomUUID();
        when(promoCodeService.update(eq(duplicate), any())).thenThrow(new ConflictException("A promo code with code 'X' already exists for this event"));
        when(promoCodeService.update(eq(used), any())).thenThrow(new ConflictException("This code has already been used; pause it and create a new one instead"));
        when(promoCodeService.update(eq(missing), any())).thenThrow(new ResourceNotFoundException("Promo code " + missing + " not found"));
        when(promoCodeService.update(eq(forbidden), any())).thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's promo codes"));
        when(promoCodeService.update(eq(badLimit), any())).thenThrow(new BadRequestException("usageLimitTotal can't be below the 5 times this code has already been used"));

        for (Object[] c : new Object[][] {{duplicate, 409}, {used, 409}, {missing, 404}, {forbidden, 403}, {badLimit, 400}}) {
            mockMvc.perform(patch("/api/v1/promo-codes/{id}", c[0]).contentType("application/json").content("{\"code\":\"X\"}"))
                    .andExpect(status().is((Integer) c[1]));
        }
    }

    @Test
    void status_paused_returns200_withPausedTrue() throws Exception {
        UUID id = UUID.randomUUID();
        PromoCode paused = promoCode(id, UUID.randomUUID());
        paused.setPaused(true);
        when(promoCodeService.setStatus(id, PromoCodeStatus.PAUSED)).thenReturn(paused);

        mockMvc.perform(put("/api/v1/promo-codes/{id}/status", id).contentType("application/json").content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));
    }

    @Test
    void status_active_returns200_withPausedFalse() throws Exception {
        UUID id = UUID.randomUUID();
        when(promoCodeService.setStatus(id, PromoCodeStatus.ACTIVE)).thenReturn(promoCode(id, UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/promo-codes/{id}/status", id).contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false));
    }

    @Test
    void status_missingOrUnknownStatus_returns400_andUnknownOrForbidden_return404And403() throws Exception {
        UUID id = UUID.randomUUID();
        for (String body : new String[] {"{}", "{\"status\":null}", "{\"status\":\"EXPIRED\"}", "{\"status\":\"paused\"}"}) {
            mockMvc.perform(put("/api/v1/promo-codes/{id}/status", id).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        UUID missing = UUID.randomUUID();
        UUID forbidden = UUID.randomUUID();
        when(promoCodeService.setStatus(eq(missing), any())).thenThrow(new ResourceNotFoundException("Promo code " + missing + " not found"));
        when(promoCodeService.setStatus(eq(forbidden), any())).thenThrow(new ForbiddenException("no"));
        mockMvc.perform(put("/api/v1/promo-codes/{id}/status", missing).contentType("application/json").content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/promo-codes/{id}/status", forbidden).contentType("application/json").content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returns204_withNoBody() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/promo-codes/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        org.mockito.Mockito.verify(promoCodeService).delete(id);
    }

    @Test
    void delete_usedUnknownForbiddenAndMalformed_mapToTheirStatuses() throws Exception {
        UUID used = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID forbidden = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ConflictException("This code has already been used, so it can't be deleted. Pause it instead.")).when(promoCodeService).delete(used);
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Promo code " + missing + " not found")).when(promoCodeService).delete(missing);
        org.mockito.Mockito.doThrow(new ForbiddenException("no")).when(promoCodeService).delete(forbidden);

        mockMvc.perform(delete("/api/v1/promo-codes/{id}", used)).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/promo-codes/{id}", missing)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/promo-codes/{id}", forbidden)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/promo-codes/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void list_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.list(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId))
                .andExpect(status().isNotFound());
    }
}
