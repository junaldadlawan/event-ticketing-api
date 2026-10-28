package com.junaldadlawan.event_ticketing_api.tickettype;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.common.NoFeesExtension;
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
import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import com.junaldadlawan.event_ticketing_api.refundpolicy.repository.RefundPolicyRepository;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A GA ticket type's {@code quantityAvailable} must always equal {@code quantityTotal} minus its live tickets (not REFUNDED or CANCELLED)
 * minus what carts hold right now, through checkout, an organizer's change of the total, a refund and a hold that expires. The Bantayog 2026
 * mismatch (100 total, 9 live tickets, 92 available) came from changing the total, which reset {@code quantityAvailable} to it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(NoFeesExtension.class)
class InventoryInvariantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private RefundPolicyRepository refundPolicyRepository;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> idempotencyKeys = new ArrayList<>();
    private UUID orgId;
    private UUID eventId;
    private UUID ticketTypeId;
    private OrganizationMember ownerMember;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        UUID ownerId = UUID.randomUUID();
        orgId = organizationRepository.save(Organization.builder().name("Inventory Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerId).documents(List.of()).build()).getId();
        ownerMember = OrganizationMember.builder().userId(ownerId).organizationId(orgId).build();
        ownerMember.getRoles().add(OrganizationRole.OWNER);
        organizationMemberRepository.save(ownerMember);
        ownerToken = token(ownerId);

        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Inventory Test Event").description("d").category("music")
                .status(EventStatus.PUBLISHED).ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        refundPolicyRepository.save(RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build());
        ticketTypeId = ticketTypeRepository.save(TicketType.builder().eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build()).quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build()).getId();
    }

    @AfterEach
    void tearDown() {
        List<UUID> orderIds = jdbc.queryForList("select distinct order_id from tickets where event_id = ?", UUID.class, eventId);
        orderIds.forEach(id -> {
            jdbc.update("delete from refunds where order_id = ?", id);
            jdbc.update("delete from payments where order_id = ?", id);
        });
        jdbc.update("delete from tickets where event_id = ?", eventId);
        orderIds.forEach(id -> jdbc.update("delete from orders where id = ?", id));
        idempotencyKeys.forEach(id -> jdbc.update("delete from checkout_idempotency_keys where id = ?", id));
        cartIds.forEach(id -> {
            jdbc.update("delete from cart_items where cart_id = ?", id);
            jdbc.update("delete from carts where id = ?", id);
        });
        jdbc.update("delete from refund_policies where event_id = ?", eventId);
        jdbc.update("delete from ticket_types where id = ?", ticketTypeId);
        jdbc.update("delete from events where id = ?", eventId);
        organizationMemberRepository.delete(ownerMember);
        organizationRepository.deleteById(orgId);
        userIds.forEach(id -> {
            jdbc.update("delete from notifications where user_id = ?", id);
            jdbc.update("delete from audit_log_entries where actor_id = ?", id);
        });
    }

    private String token(UUID userId) {
        userIds.add(userId);
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(userId).email("inventory-it-" + UUID.randomUUID() + "@test.local")
                .role(Role.CUSTOMER).build());
    }

    private UUID cartWith(String buyer, int quantity) throws Exception {
        UUID cartId = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/v1/carts").header("Authorization", buyer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
        cartIds.add(cartId);
        mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", buyer).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":" + quantity + "}")).andExpect(status().isCreated());
        return cartId;
    }

    private UUID checkout(String buyer, UUID cartId) throws Exception {
        UUID key = UUID.randomUUID();
        idempotencyKeys.add(key);
        String body = mockMvc.perform(post("/api/v1/carts/{id}/checkout", cartId).header("Authorization", buyer).header("Idempotency-Key", key.toString())
                .contentType("application/json").content("{\"paymentMethodToken\":\"tok_ok\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    /** quantityAvailable == quantityTotal - live tickets - held. */
    private void assertInvariant(int expectedAvailable) {
        int total = jdbc.queryForObject("select quantity_total from ticket_types where id = ?", Integer.class, ticketTypeId);
        int available = jdbc.queryForObject("select quantity_available from ticket_types where id = ?", Integer.class, ticketTypeId);
        int live = jdbc.queryForObject("select count(*) from tickets where ticket_type_id = ? and status not in ('REFUNDED', 'CANCELLED')", Integer.class, ticketTypeId);
        int held = jdbc.queryForObject("select coalesce(sum(quantity), 0) from cart_items where ticket_type_id = ?", Integer.class, ticketTypeId);
        assertThat(available).as("available = total %d - live %d - held %d", total, live, held).isEqualTo(total - live - held);
        assertThat(available).isEqualTo(expectedAvailable);
    }

    @Test
    void quantityAvailableAlwaysMatchesTheTotalMinusLiveAndHeldTickets() throws Exception {
        String buyer = token(UUID.randomUUID());

        // a hold, then checkout
        UUID cart = cartWith(buyer, 3);
        assertInvariant(7);
        UUID order = checkout(buyer, cart);
        assertInvariant(7);

        // another buyer holds 2 while the organizer raises the total: what is sold or held stays taken (was: reset to 20)
        String other = token(UUID.randomUUID());
        UUID otherCart = cartWith(other, 2);
        mockMvc.perform(patch("/api/v1/ticket-types/{id}", ticketTypeId).header("Authorization", ownerToken).contentType("application/json")
                .content("{\"quantityTotal\":20}")).andExpect(status().isOk());
        assertInvariant(15);
        // and a total below what is taken is refused
        mockMvc.perform(patch("/api/v1/ticket-types/{id}", ticketTypeId).header("Authorization", ownerToken).contentType("application/json")
                .content("{\"quantityTotal\":4}")).andExpect(status().isConflict());
        assertInvariant(15);

        // that hold expires and is released when the cart is next read
        jdbc.update("update cart_items set hold_expires_at = now() - interval '1 minute' where cart_id = ?", otherCart);
        mockMvc.perform(get("/api/v1/carts/{id}", otherCart).header("Authorization", other)).andExpect(status().isOk());
        assertInvariant(17);

        // a full refund gives the tickets back
        mockMvc.perform(post("/api/v1/orders/{id}/refunds", order).header("Authorization", ownerToken).contentType("application/json")
                .content("{\"reason\":\"inventory test\"}")).andExpect(status().isCreated());
        assertInvariant(20);

        // and they can be sold again
        checkout(buyer, cartWith(buyer, 4));
        assertInvariant(16);
    }
}
