package com.junaldadlawan.event_ticketing_api.cart.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.cart.dto.AppliedPromoCodeResponse;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemResponse;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link CartController}'s own behavior (request validation,
 * response mapping, status codes) — mirrors {@code TicketTypeControllerTest}.
 * Security-filter enforcement (401/403, buyer-only access) is exercised
 * separately in {@code CartAccessIntegrationTest}.
 */
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private CartResponse cartResponse(UUID id) {
        return new CartResponse(id, UUID.randomUUID(), List.of(), null, new MoneyDto(0L, "USD"), Instant.now(), Instant.now());
    }

    private CartResponse cartResponseWithItem(UUID id, UUID itemId) {
        CartItemResponse item = new CartItemResponse(itemId, UUID.randomUUID(), null, 2, Instant.now().plusSeconds(900), Instant.now());
        return new CartResponse(id, UUID.randomUUID(), List.of(item), null, new MoneyDto(2000L, "USD"), Instant.now(), Instant.now());
    }

    // ---- create() ----

    @Test
    void create_returns201() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.create()).thenReturn(cartResponse(cartId));

        mockMvc.perform(post("/api/v1/carts"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cartId.toString()));
    }

    // ---- get() ----

    @Test
    void get_existingCart_returns200() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.get(cartId)).thenReturn(cartResponse(cartId));

        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId.toString()));
    }

    @Test
    void get_notOwner_returns403() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.get(cartId)).thenThrow(new ForbiddenException("Only the cart's own buyer may access this cart"));

        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unknownCart_returns404() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.get(cartId)).thenThrow(new ResourceNotFoundException("Cart " + cartId + " not found"));

        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId))
                .andExpect(status().isNotFound());
    }

    // ---- addItem() ----

    @Test
    void addItem_validRequest_returns201() throws Exception {
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(cartService.addItem(eq(cartId), any())).thenReturn(cartResponseWithItem(cartId, itemId));

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .contentType("application/json")
                        .content("""
                                {"ticketTypeId":"%s","quantity":2}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void addItem_missingTicketTypeId_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .contentType("application/json")
                        .content("""
                                {"quantity":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_nonPositiveQuantity_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .contentType("application/json")
                        .content("""
                                {"ticketTypeId":"%s","quantity":0}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_unknownTicketType_returns404() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.addItem(eq(cartId), any())).thenThrow(new ResourceNotFoundException("Ticket type not found"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .contentType("application/json")
                        .content("""
                                {"ticketTypeId":"%s","quantity":1}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_noLongerAvailable_returns409() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.addItem(eq(cartId), any())).thenThrow(new ConflictException("Seat or GA quantity no longer available"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .contentType("application/json")
                        .content("""
                                {"ticketTypeId":"%s","quantity":1}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_seatIdRequiredForReservedSeating_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.addItem(eq(cartId), any())).thenThrow(new BadRequestException("seatId is required for reserved-seating ticket types"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .contentType("application/json")
                        .content("""
                                {"ticketTypeId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // ---- removeItem() ----

    @Test
    void removeItem_returns204() throws Exception {
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/carts/{cartId}/items/{itemId}", cartId, itemId))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeItem_unknownItem_returns404() throws Exception {
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Cart item not found"))
                .when(cartService).removeItem(cartId, itemId);

        mockMvc.perform(delete("/api/v1/carts/{cartId}/items/{itemId}", cartId, itemId))
                .andExpect(status().isNotFound());
    }

    // ---- applyPromoCode() ----

    @Test
    void applyPromoCode_validRequest_returns200() throws Exception {
        UUID cartId = UUID.randomUUID();
        CartResponse withPromo = new CartResponse(cartId, UUID.randomUUID(), List.of(),
                new AppliedPromoCodeResponse("SAVE10", new MoneyDto(100L, "USD")), new MoneyDto(900L, "USD"), Instant.now(), Instant.now());
        when(cartService.applyPromoCode(eq(cartId), eq("SAVE10"))).thenReturn(withPromo);

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode.code").value("SAVE10"));
    }

    @Test
    void applyPromoCode_blankCode_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .contentType("application/json")
                        .content("""
                                {"code":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyPromoCode_inapplicable_returns422() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.applyPromoCode(eq(cartId), any()))
                .thenThrow(new UnprocessableEntityException("Promo code is invalid or inapplicable to this cart"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .contentType("application/json")
                        .content("""
                                {"code":"BADCODE"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- removePromoCode() ----

    @Test
    void removePromoCode_returns200() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.removePromoCode(cartId)).thenReturn(cartResponse(cartId));

        mockMvc.perform(delete("/api/v1/carts/{cartId}/promo-code", cartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId.toString()));
    }
}
