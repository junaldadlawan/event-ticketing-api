package com.junaldadlawan.event_ticketing_api.cart;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A promo code paused or deleted after a buyer applied it must not leave the cart stuck: a deleted code is cleared
 * from the cart; a paused one stays visible (discount 0, status PAUSED) so the buyer can remove it and pay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(com.junaldadlawan.event_ticketing_api.common.NoFeesExtension.class)
class CartPromoCodeLifecycleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;

    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> idempotencyKeys = new ArrayList<>();
    private UUID orgId;
    private UUID eventId;
    private UUID ticketTypeId;
    private PromoCode promoCode;
    private User buyer;
    private String ownerToken;
    private String buyerToken;

    @BeforeEach
    void setUp() {
        User owner = User.builder().id(UUID.randomUUID()).email("promo-owner-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        buyer = User.builder().id(UUID.randomUUID()).email("promo-buyer-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        ownerToken = "Bearer " + jwtService.generateAccessToken(owner);
        buyerToken = "Bearer " + jwtService.generateAccessToken(buyer);
        orgId = organizationRepository.save(Organization.builder().name("Promo Lifecycle Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(owner.getId()).documents(List.of()).build()).getId();
        OrganizationMember membership = OrganizationMember.builder().userId(owner.getId()).organizationId(orgId).build();
        membership.getRoles().add(OrganizationRole.OWNER);
        organizationMemberRepository.save(membership);
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Promo Lifecycle Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED).ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        ticketTypeId = ticketTypeRepository.save(TicketType.builder().eventId(eventId).name("General Admission")
                .kind(TicketTypeKind.GENERAL_ADMISSION).price(Money.builder().amount(1000).currency("PHP").build())
                .quantityTotal(10).quantityAvailable(10).saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build()).getId();
        promoCode = promoCodeRepository.save(PromoCode.builder().eventId(eventId)
                .code("JUN" + UUID.randomUUID().toString().substring(0, 4).toUpperCase())
                .discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS)).validUntil(Instant.now().plus(5, ChronoUnit.DAYS)).build());
    }

    @AfterEach
    void tearDown() {
        orderIds.forEach(id -> {
            jdbc.update("delete from tickets where order_id = ?", id);
            jdbc.update("delete from payments where order_id = ?", id);
            jdbc.update("delete from orders where id = ?", id);
        });
        idempotencyKeys.forEach(id -> jdbc.update("delete from checkout_idempotency_keys where id = ?", id));
        jdbc.update("delete from notifications where user_id = ?", buyer.getId());
        cartIds.forEach(id -> {
            jdbc.update("delete from cart_items where cart_id = ?", id);
            jdbc.update("delete from carts where id = ?", id);
        });
        jdbc.update("delete from promo_codes where event_id = ?", eventId);
        jdbc.update("delete from ticket_types where event_id = ?", eventId);
        jdbc.update("delete from audit_log_entries where organization_id = ?", orgId);
        eventRepository.deleteById(eventId);
        jdbc.update("delete from organization_member_roles where organization_id = ?", orgId);
        jdbc.update("delete from organization_members where organization_id = ?", orgId);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void deletingAnAppliedCode_clearsItFromTheCart_andCheckoutGoesThrough() throws Exception {
        UUID cartId = cartWithCodeApplied();

        mockMvc.perform(delete("/api/v1/promo-codes/{id}", promoCode.getId()).header("Authorization", ownerToken))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select promo_code_id from carts where id = ?", UUID.class, cartId)).isNull();
        mockMvc.perform(get("/api/v1/carts/{id}", cartId).header("Authorization", buyerToken))
                .andExpect(jsonPath("$.appliedPromoCode").isEmpty());
        assertThat(checkout(cartId).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void aPausedCode_staysVisibleWithNoDiscount_blocksCheckoutByName_andCanBeRemoved() throws Exception {
        UUID cartId = cartWithCodeApplied();

        mockMvc.perform(put("/api/v1/promo-codes/{id}/status", promoCode.getId()).header("Authorization", ownerToken)
                .contentType("application/json").content("{\"status\":\"PAUSED\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/carts/{id}", cartId).header("Authorization", buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode.code").value(promoCode.getCode()))
                .andExpect(jsonPath("$.appliedPromoCode.status").value("PAUSED"))
                .andExpect(jsonPath("$.appliedPromoCode.discountAmount.amount").value(0))
                .andExpect(jsonPath("$.total.amount").value(1000));

        MvcResult refused = checkout(cartId);
        assertThat(refused.getResponse().getStatus()).isEqualTo(422);
        assertThat(objectMapper.readTree(refused.getResponse().getContentAsString()).get("detail").asText())
                .isEqualTo("Promo code " + promoCode.getCode() + " is paused. Remove it from your cart to check out.");

        mockMvc.perform(delete("/api/v1/carts/{id}/promo-code", cartId).header("Authorization", buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode").isEmpty());
        assertThat(checkout(cartId).getResponse().getStatus()).isEqualTo(201);
    }

    // ---- helpers ----

    private UUID cartWithCodeApplied() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/carts").header("Authorization", buyerToken))
                .andExpect(status().isCreated()).andReturn();
        UUID cartId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        cartIds.add(cartId);
        mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", buyerToken)
                .contentType("application/json").content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/carts/{id}/promo-code", cartId).header("Authorization", buyerToken)
                        .contentType("application/json").content("{\"code\":\"" + promoCode.getCode() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode.status").value("ACTIVE"))
                .andExpect(jsonPath("$.appliedPromoCode.discountAmount.amount").value(100));
        return cartId;
    }

    private MvcResult checkout(UUID cartId) throws Exception {
        UUID key = UUID.randomUUID();
        idempotencyKeys.add(key);
        MvcResult result = mockMvc.perform(post("/api/v1/carts/{id}/checkout", cartId).header("Authorization", buyerToken)
                .header("Idempotency-Key", key.toString()).contentType("application/json")
                .content("{\"paymentMethodToken\":\"tok_ok\"}")).andReturn();
        if (result.getResponse().getStatus() == 201) {
            orderIds.add(UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText()));
        }
        return result;
    }
}
