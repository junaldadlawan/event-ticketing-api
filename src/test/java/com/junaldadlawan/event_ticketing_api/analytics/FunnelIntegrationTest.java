package com.junaldadlawan.event_ticketing_api.analytics;

import com.junaldadlawan.event_ticketing_api.analytics.service.FunnelRetentionJob;
import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The event funnel (BR-ANALYTICS-003) on the real database: recording views, the cart log on every cart path, the funnel report, retention. */
@SpringBootTest(properties = "app.analytics.cart-activity.enabled=true")
@AutoConfigureMockMvc
@ExtendWith(NoFeesExtension.class)
class FunnelIntegrationTest {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

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
    private FunnelRetentionJob retentionJob;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> idempotencyKeys = new ArrayList<>();
    private UUID orgId;
    private UUID eventId;
    private UUID typeA;
    private UUID typeB;
    private String owner;
    private String organizer;
    private String admin;
    private String otherOwner;

    @BeforeEach
    void setUp() {
        orgId = organization();
        owner = member(orgId, OrganizationRole.OWNER);
        organizer = member(orgId, OrganizationRole.ORGANIZER);
        otherOwner = member(organization(), OrganizationRole.OWNER);
        admin = token(UUID.randomUUID(), Role.ADMIN);
        eventId = event(EventStatus.PUBLISHED);
        typeA = ticketType("General", 0);
        typeB = ticketType("VIP", 1);
    }

    @AfterEach
    void tearDown() {
        List<UUID> orderIds = eventIds.stream().flatMap(id -> jdbc.queryForList("select distinct order_id from tickets where event_id = ?", UUID.class, id).stream()).toList();
        orderIds.forEach(id -> jdbc.update("delete from payments where order_id = ?", id));
        eventIds.forEach(id -> {
            jdbc.update("delete from tickets where event_id = ?", id);
            jdbc.update("delete from event_views where event_id = ?", id);
            jdbc.update("delete from cart_activity where event_id = ?", id);
        });
        orderIds.forEach(id -> jdbc.update("delete from orders where id = ?", id));
        idempotencyKeys.forEach(id -> jdbc.update("delete from checkout_idempotency_keys where id = ?", id));
        cartIds.forEach(id -> {
            jdbc.update("delete from cart_items where cart_id = ?", id);
            jdbc.update("delete from carts where id = ?", id);
        });
        ticketTypeIds.forEach(ticketTypeRepository::deleteById);
        eventIds.forEach(eventRepository::deleteById);
        members.forEach(organizationMemberRepository::delete);
        orgIds.forEach(organizationRepository::deleteById);
        userIds.forEach(id -> {
            jdbc.update("delete from notifications where user_id = ?", id);
            jdbc.update("delete from audit_log_entries where actor_id = ?", id);
        });
    }

    // ---- fixtures ----

    private UUID organization() {
        UUID id = organizationRepository.save(Organization.builder().name("Funnel Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        orgIds.add(id);
        return id;
    }

    private String token(UUID userId, Role role) {
        userIds.add(userId);
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(userId).email("funnel-it-" + UUID.randomUUID() + "@test.local").role(role).build());
    }

    private String member(UUID org, OrganizationRole role) {
        UUID userId = UUID.randomUUID();
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(org).build();
        member.getRoles().add(role);
        members.add(organizationMemberRepository.save(member));
        return token(userId, Role.CUSTOMER);
    }

    private UUID event(EventStatus status) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID id = eventRepository.save(Event.builder().organizationId(orgId).title("Funnel Test Event").description("d").category("music").status(status)
                .ticketPrefix("F" + UUID.randomUUID().toString().substring(0, 2).toUpperCase()).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS))
                .timezone("Asia/Manila").build()).getId();
        eventIds.add(id);
        return id;
    }

    private UUID ticketType(String name, int position) {
        TicketType type = TicketType.builder().eventId(eventId).name(name).kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build()).quantityTotal(50).quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build();
        type.setPosition(position);
        UUID id = ticketTypeRepository.save(type).getId();
        ticketTypeIds.add(id);
        return id;
    }

    /** A view from a fresh client address, so the per-client rate limit of one test never touches another. */
    private ResultActions view(UUID event, String caller, String visitorId) throws Exception {
        var request = post("/api/v1/events/{id}/views", event).header("X-Forwarded-For", "10.0.0.1, " + UUID.randomUUID())
                .contentType("application/json").content(visitorId == null ? "{}" : "{\"visitorId\":\"" + visitorId + "\"}");
        return mockMvc.perform(caller == null ? request : request.header("Authorization", caller));
    }

    private long views(UUID event) {
        return jdbc.queryForObject("select count(*) from event_views where event_id = ?", Long.class, event);
    }

    private UUID cart(String buyer) throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/v1/carts").header("Authorization", buyer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
        cartIds.add(id);
        return id;
    }

    private UUID add(String buyer, UUID cart, UUID type, int quantity) throws Exception {
        JsonNode items = objectMapper.readTree(mockMvc.perform(post("/api/v1/carts/{id}/items", cart).header("Authorization", buyer)
                .contentType("application/json").content("{\"ticketTypeId\":\"" + type + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("items");
        return UUID.fromString(items.get(0).get("id").asText());
    }

    private void setQuantity(String buyer, UUID cart, UUID item, int quantity) throws Exception {
        mockMvc.perform(patch("/api/v1/carts/{id}/items/{item}", cart, item).header("Authorization", buyer).contentType("application/json")
                .content("{\"quantity\":" + quantity + "}")).andExpect(status().isOk());
    }

    private void checkout(String buyer, UUID cart) throws Exception {
        UUID key = UUID.randomUUID();
        idempotencyKeys.add(key);
        mockMvc.perform(post("/api/v1/carts/{id}/checkout", cart).header("Authorization", buyer).header("Idempotency-Key", key.toString())
                .contentType("application/json").content("{\"paymentMethodToken\":\"tok_ok\"}")).andExpect(status().isCreated());
    }

    private List<String> activity(UUID item) {
        return jdbc.queryForList("select action || ':' || quantity from cart_activity where cart_item_id = ? order by occurred_at, action", String.class, item);
    }

    // ---- POST /events/{id}/views ----

    @Test
    void aViewIsRecordedOncePerVisitorPer30Minutes_withAHashedKey() throws Exception {
        String visitor = UUID.randomUUID().toString();
        view(eventId, null, visitor).andExpect(status().isNoContent());
        view(eventId, null, visitor).andExpect(status().isNoContent());   // the repeat is not stored
        assertThat(views(eventId)).isEqualTo(1);
        String key = jdbc.queryForObject("select visitor_key from event_views where event_id = ?", String.class, eventId);
        assertThat(key).hasSize(64).isNotEqualTo(visitor);   // SHA-256 hex, never the raw id

        // half an hour later the same visitor counts again
        jdbc.update("update event_views set viewed_at = viewed_at - interval '31 minutes' where event_id = ?", eventId);
        view(eventId, null, visitor).andExpect(status().isNoContent());
        assertThat(views(eventId)).isEqualTo(2);

        // a signed-in buyer is keyed by their account, with or without a visitor id
        UUID buyerId = UUID.randomUUID();
        String buyer = token(buyerId, Role.CUSTOMER);
        view(eventId, buyer, null).andExpect(status().isNoContent());
        view(eventId, buyer, UUID.randomUUID().toString()).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from event_views where event_id = ? and user_id = ? and visitor_key = ?", Long.class,
                eventId, buyerId, buyerId.toString())).isEqualTo(1);
        view(eventId, null, null).andExpect(status().isNoContent());   // anonymous without a visitor id: nothing to count
        assertThat(views(eventId)).isEqualTo(3);
    }

    @Test
    void theEventsOwnTeamAdminsAndEventsThatAreNotPublic_areNotCounted() throws Exception {
        view(eventId, owner, null).andExpect(status().isNoContent());
        view(eventId, organizer, null).andExpect(status().isNoContent());
        view(eventId, admin, null).andExpect(status().isNoContent());
        assertThat(views(eventId)).isZero();
        view(eventId, otherOwner, null).andExpect(status().isNoContent());   // another organization's owner is just a visitor here
        assertThat(views(eventId)).isEqualTo(1);

        for (EventStatus status : List.of(EventStatus.DRAFT, EventStatus.CANCELLED, EventStatus.SUSPENDED)) {
            UUID hidden = event(status);
            view(hidden, null, UUID.randomUUID().toString()).andExpect(status().isNoContent());
            assertThat(views(hidden)).isZero();
        }
        view(UUID.randomUUID(), null, UUID.randomUUID().toString()).andExpect(status().isNoContent());   // unknown: same answer
        UUID completed = event(EventStatus.COMPLETED);
        view(completed, null, UUID.randomUUID().toString()).andExpect(status().isNoContent());
        assertThat(views(completed)).isEqualTo(1);
    }

    @Test
    void oneClientIsLimitedTo60ViewsAMinute() throws Exception {
        String client = "203.0.113." + UUID.randomUUID();
        for (int i = 0; i < 61; i++) {
            mockMvc.perform(post("/api/v1/events/{id}/views", eventId).header("X-Forwarded-For", client).contentType("application/json")
                    .content("{\"visitorId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isNoContent());
        }
        assertThat(views(eventId)).isEqualTo(60);
    }

    // ---- the cart log ----

    @Test
    void everyCartPath_writesItsActivityRow() throws Exception {
        String buyer = token(UUID.randomUUID(), Role.CUSTOMER);
        UUID cart = cart(buyer);
        UUID line = add(buyer, cart, typeA, 2);
        add(buyer, cart, typeA, 1);   // merged into the same line
        setQuantity(buyer, cart, line, 1);
        setQuantity(buyer, cart, line, 2);
        checkout(buyer, cart);
        assertThat(activity(line)).containsExactly("ADD:2", "ADD:1", "REMOVE:2", "ADD:1", "CHECKOUT:2");

        String second = token(UUID.randomUUID(), Role.CUSTOMER);
        UUID expiring = add(second, cart(second), typeB, 1);
        jdbc.update("update cart_items set hold_expires_at = now() - interval '1 minute' where id = ?", expiring);
        UUID expiringCart = cartIds.get(cartIds.size() - 1);
        mockMvc.perform(get("/api/v1/carts/{id}", expiringCart).header("Authorization", second)).andExpect(status().isOk());
        assertThat(activity(expiring)).containsExactly("ADD:1", "EXPIRE:1");

        String third = token(UUID.randomUUID(), Role.CUSTOMER);
        UUID removedCart = cart(third);
        UUID removed = add(third, removedCart, typeB, 1);
        mockMvc.perform(delete("/api/v1/carts/{id}/items/{item}", removedCart, removed).header("Authorization", third)).andExpect(status().isNoContent());
        assertThat(activity(removed)).containsExactly("ADD:1", "REMOVE:1");

        assertThat(jdbc.queryForObject("select count(distinct event_id) from cart_activity where cart_item_id in (?, ?, ?)", Long.class, line, expiring, removed))
                .isEqualTo(1);
    }

    // ---- GET /events/{id}/analytics/funnel ----

    @Test
    void theFunnelAddsUpTheViewsAndTheCartLog_perDayAndPerTicketType() throws Exception {
        view(eventId, null, "visitor-a");
        view(eventId, null, "visitor-b");
        String buyer = token(UUID.randomUUID(), Role.CUSTOMER);
        view(eventId, buyer, null);
        UUID cart = cart(buyer);
        UUID line = add(buyer, cart, typeA, 2);
        add(buyer, cart, typeA, 1);
        setQuantity(buyer, cart, line, 1);
        setQuantity(buyer, cart, line, 2);
        checkout(buyer, cart);   // A: 3 adds, 4 added, 2 removed, 2 checked out
        String second = token(UUID.randomUUID(), Role.CUSTOMER);
        UUID expiringCart = cart(second);
        UUID expiring = add(second, expiringCart, typeB, 1);
        jdbc.update("update cart_items set hold_expires_at = now() - interval '1 minute' where id = ?", expiring);
        mockMvc.perform(get("/api/v1/carts/{id}", expiringCart).header("Authorization", second)).andExpect(status().isOk());
        String third = token(UUID.randomUUID(), Role.CUSTOMER);
        UUID removedCart = cart(third);
        UUID removed = add(third, removedCart, typeB, 1);
        mockMvc.perform(delete("/api/v1/carts/{id}/items/{item}", removedCart, removed).header("Authorization", third)).andExpect(status().isNoContent());
        // B: 2 adds by 2 buyers, 1 expired, 1 removed

        LocalDate today = LocalDate.now(MANILA);
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel?from={from}&to={to}", eventId, today.minusDays(2), today).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Manila"))
                .andExpect(jsonPath("$.from").value(today.minusDays(2).toString())).andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.views").value(3)).andExpect(jsonPath("$.uniqueVisitors").value(3))
                .andExpect(jsonPath("$.addToCart.actions").value(5)).andExpect(jsonPath("$.addToCart.quantity").value(6))
                .andExpect(jsonPath("$.addToCart.uniqueBuyers").value(3))
                .andExpect(jsonPath("$.checkedOut.quantity").value(2)).andExpect(jsonPath("$.checkedOut.uniqueBuyers").value(1))
                .andExpect(jsonPath("$.viewToCartRate").value(closeTo(1.0, 1e-9)))
                .andExpect(jsonPath("$.viewToPurchaseRate").value(closeTo(1.0 / 3, 1e-9)))
                .andExpect(jsonPath("$.daily.length()").value(3))
                .andExpect(jsonPath("$.daily[0].views").value(0)).andExpect(jsonPath("$.daily[0].addToCartQuantity").value(0))
                .andExpect(jsonPath("$.daily[1].checkedOutQuantity").value(0))
                .andExpect(jsonPath("$.daily[2].day").value(today.toString())).andExpect(jsonPath("$.daily[2].views").value(3))
                .andExpect(jsonPath("$.daily[2].uniqueVisitors").value(3)).andExpect(jsonPath("$.daily[2].addToCartQuantity").value(6))
                .andExpect(jsonPath("$.daily[2].checkedOutQuantity").value(2))
                .andExpect(jsonPath("$.ticketTypes.length()").value(2))
                .andExpect(jsonPath("$.ticketTypes[0].ticketTypeId").value(typeA.toString())).andExpect(jsonPath("$.ticketTypes[0].name").value("General"))
                .andExpect(jsonPath("$.ticketTypes[0].addToCartActions").value(3)).andExpect(jsonPath("$.ticketTypes[0].addedQuantity").value(4))
                .andExpect(jsonPath("$.ticketTypes[0].uniqueBuyersAdding").value(1)).andExpect(jsonPath("$.ticketTypes[0].checkedOutQuantity").value(2))
                .andExpect(jsonPath("$.ticketTypes[0].removedQuantity").value(2)).andExpect(jsonPath("$.ticketTypes[0].expiredQuantity").value(0))
                .andExpect(jsonPath("$.ticketTypes[0].cartConversionRate").value(closeTo(0.5, 1e-9)))
                .andExpect(jsonPath("$.ticketTypes[1].name").value("VIP"))
                .andExpect(jsonPath("$.ticketTypes[1].addToCartActions").value(2)).andExpect(jsonPath("$.ticketTypes[1].uniqueBuyersAdding").value(2))
                .andExpect(jsonPath("$.ticketTypes[1].removedQuantity").value(1)).andExpect(jsonPath("$.ticketTypes[1].expiredQuantity").value(1))
                .andExpect(jsonPath("$.ticketTypes[1].cartConversionRate").value(closeTo(0.0, 1e-9)))
                .andExpect(jsonPath("$.trackingSince").exists());
    }

    @Test
    void anEmptyFunnel_hasEveryDayAndTicketTypeAtZero_andNoRates() throws Exception {
        LocalDate today = LocalDate.now(MANILA);
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel", eventId).header("Authorization", organizer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.from").value(today.toString()))   // the default starts the day the event was created: today
                .andExpect(jsonPath("$.daily.length()").value(1)).andExpect(jsonPath("$.daily[0].views").value(0))
                .andExpect(jsonPath("$.daily[0].checkedOutQuantity").value(0))
                .andExpect(jsonPath("$.views").value(0)).andExpect(jsonPath("$.viewToCartRate").value(nullValue()))
                .andExpect(jsonPath("$.ticketTypes[0].addedQuantity").value(0)).andExpect(jsonPath("$.ticketTypes[0].cartConversionRate").value(nullValue()))
                .andExpect(jsonPath("$.ticketTypes[1].checkedOutQuantity").value(0));
    }

    @Test
    void onlyTheEventsTeamOrAnAdminSeesTheFunnel_andTheRangeIsChecked() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel", eventId).header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel", eventId).header("Authorization", otherOwner)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel", eventId).header("Authorization", token(UUID.randomUUID(), Role.CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel", eventId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel?from=2026-02-01&to=2026-01-01", eventId).header("Authorization", owner))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/events/{id}/analytics/funnel?from=2025-01-01&to=2026-01-02", eventId).header("Authorization", owner))
                .andExpect(status().isBadRequest());   // over 366 days
    }

    // ---- retention ----

    @Test
    void rowsOlderThan13MonthsAreRemoved() throws Exception {
        for (String age : List.of("14 months", "12 months")) {
            jdbc.update("insert into event_views (id, event_id, visitor_key, viewed_at) values (?, ?, ?, now() - ?::interval)",
                    UUID.randomUUID(), eventId, "old-" + age, age);
            jdbc.update("insert into cart_activity (id, event_id, ticket_type_id, cart_item_id, buyer_id, quantity, action, occurred_at) "
                    + "values (?, ?, ?, ?, ?, 1, 'ADD', now() - ?::interval)", UUID.randomUUID(), eventId, typeA, UUID.randomUUID(), UUID.randomUUID(), age);
        }
        retentionJob.purge();
        assertThat(jdbc.queryForList("select visitor_key from event_views where event_id = ?", String.class, eventId)).containsExactly("old-12 months");
        assertThat(jdbc.queryForObject("select count(*) from cart_activity where event_id = ?", Long.class, eventId)).isEqualTo(1);
    }
}
