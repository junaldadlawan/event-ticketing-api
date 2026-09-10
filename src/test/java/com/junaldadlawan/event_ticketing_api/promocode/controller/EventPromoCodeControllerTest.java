package com.junaldadlawan.event_ticketing_api.promocode.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventPromoCodeController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors
 * {@code EventTicketTypeControllerTest}. Security-filter enforcement
 * (401/403, cross-org rejection) is exercised separately in
 * {@code EventPromoCodeAccessIntegrationTest}.
 */
@WebMvcTest(EventPromoCodeController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventPromoCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromoCodeService promoCodeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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

    @Test
    void list_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(promoCodeService.list(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId))
                .andExpect(status().isNotFound());
    }
}
