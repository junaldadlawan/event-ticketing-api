package com.junaldadlawan.event_ticketing_api.cart;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adding the same general-admission ticket type twice merges into one line, and the quantity of a line can be changed (real DB, real row locks).
 * The GA ticket type has 10 tickets and a limit of 6 per order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(com.junaldadlawan.event_ticketing_api.common.NoFeesExtension.class)
class CartLineQuantityIntegrationTest {

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
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private SeatMapRepository seatMapRepository;
    @Autowired
    private SeatRepository seatRepository;

    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> seatIds = new ArrayList<>();
    private UUID seatMapId;
    private UUID orgId;
    private UUID eventId;
    private UUID ga;
    private UUID seated;
    private String buyer;
    private String otherBuyer;

    @BeforeEach
    void setUp() {
        buyer = token();
        otherBuyer = token();
        orgId = organizationRepository.save(Organization.builder().name("Line Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Line Test Event").description("d").category("music")
                .status(EventStatus.ON_SALE).ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        ga = ticketType("GA", TicketTypeKind.GENERAL_ADMISSION);
        seated = ticketType("Seated", TicketTypeKind.RESERVED_SEATING);
        seatMapId = seatMapRepository.save(SeatMap.builder().eventId(eventId).build()).getId();
    }

    @AfterEach
    void tearDown() {
        cartIds.forEach(id -> cartItemRepository.findByCartId(id).forEach(cartItemRepository::delete));
        cartIds.forEach(cartRepository::deleteById);
        seatIds.forEach(seatRepository::deleteById);
        seatMapRepository.deleteById(seatMapId);
        jdbc.update("delete from ticket_types where event_id = ?", eventId);
        eventRepository.deleteById(eventId);
        organizationRepository.deleteById(orgId);
    }

    // ---- fixtures ----

    private String token() {
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(UUID.randomUUID())
                .email("line-buyer-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build());
    }

    private UUID ticketType(String name, TicketTypeKind kind) {
        return ticketTypeRepository.save(TicketType.builder().eventId(eventId).name(name).kind(kind)
                .price(Money.builder().amount(1_000).currency("USD").build()).quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(6).build()).getId();
    }

    private UUID seat() {
        UUID id = seatRepository.save(Seat.builder().seatMapId(seatMapId).section("A").row("1")
                .seatNumber(UUID.randomUUID().toString().substring(0, 4)).status(SeatStatus.AVAILABLE).build()).getId();
        seatIds.add(id);
        return id;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID newCart(String caller) throws Exception {
        UUID id = UUID.fromString(body(mockMvc.perform(post("/api/v1/carts").header("Authorization", caller)).andExpect(status().isCreated()).andReturn()).get("id").asText());
        cartIds.add(id);
        return id;
    }

    private MvcResult add(String caller, UUID cartId, UUID ticketTypeId, int quantity, int expected) throws Exception {
        return mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", caller).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":" + quantity + "}")).andExpect(status().is(expected)).andReturn();
    }

    private MvcResult addSeat(UUID cartId, UUID seatId) throws Exception {
        return mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", buyer).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + seated + "\",\"seatId\":\"" + seatId + "\"}")).andExpect(status().isCreated()).andReturn();
    }

    private MvcResult setQuantity(String caller, UUID cartId, UUID itemId, int quantity, int expected) throws Exception {
        return mockMvc.perform(patch("/api/v1/carts/{id}/items/{item}", cartId, itemId).header("Authorization", caller).contentType("application/json")
                .content("{\"quantity\":" + quantity + "}")).andExpect(status().is(expected)).andReturn();
    }

    private int available(UUID ticketTypeId) {
        return jdbc.queryForObject("select quantity_available from ticket_types where id = ?", Integer.class, ticketTypeId);
    }

    private UUID lineOf(JsonNode cart, UUID ticketTypeId) {
        for (JsonNode item : cart.get("items")) {
            if (item.get("ticketTypeId").asText().equals(ticketTypeId.toString())) {
                return UUID.fromString(item.get("id").asText());
            }
        }
        throw new AssertionError("no line for " + ticketTypeId);
    }

    // ---- add merges ----

    @Test
    void addingTheSameTicketTypeTwice_makesOneLineWithTheSummedQuantity_andKeepsTheFirstHold() throws Exception {
        UUID cartId = newCart(buyer);
        JsonNode first = body(add(buyer, cartId, ga, 1, 201));
        Instant hold = Instant.parse(first.get("items").get(0).get("holdExpiresAt").asText());

        JsonNode second = body(add(buyer, cartId, ga, 2, 201));

        assertThat(second.get("items")).hasSize(1);
        assertThat(second.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(Instant.parse(second.get("items").get(0).get("holdExpiresAt").asText()).truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hold.truncatedTo(ChronoUnit.MILLIS));   // not extended
        assertThat(available(ga)).isEqualTo(7);
        // 3 tickets at 1,000: the cart prices the merged line from its quantity
        assertThat(second.get("total").get("amount").asLong()).isEqualTo(3_000);
    }

    @Test
    void theMaxPerOrderLimitAppliesToTheMergedQuantity() throws Exception {
        UUID cartId = newCart(buyer);
        add(buyer, cartId, ga, 7, 409);   // more than 6 at once
        add(buyer, cartId, ga, 4, 201);
        add(buyer, cartId, ga, 3, 409);   // 4 + 3 = 7 > 6

        assertThat(cartItemRepository.findByCartId(cartId).get(0).getQuantity()).isEqualTo(4);
        assertThat(available(ga)).isEqualTo(6);
        add(buyer, cartId, ga, 2, 201);   // exactly 6
        assertThat(available(ga)).isEqualTo(4);
    }

    @Test
    void seatedItemsStayOneLinePerSeat_andTheirQuantityCannotChange() throws Exception {
        UUID cartId = newCart(buyer);
        addSeat(cartId, seat());
        JsonNode both = body(addSeat(cartId, seat()));

        assertThat(both.get("items")).hasSize(2);
        setQuantity(buyer, cartId, UUID.fromString(both.get("items").get(0).get("id").asText()), 2, 409);
    }

    // ---- change the quantity ----

    @Test
    void changingTheQuantity_movesTheHeldInventoryByTheDifference_andKeepsTheHold() throws Exception {
        UUID cartId = newCart(buyer);
        JsonNode cart = body(add(buyer, cartId, ga, 2, 201));
        UUID line = lineOf(cart, ga);
        Instant hold = cartItemRepository.findById(line).orElseThrow().getHoldExpiresAt();
        assertThat(available(ga)).isEqualTo(8);

        JsonNode up = body(setQuantity(buyer, cartId, line, 5, 200));
        assertThat(available(ga)).isEqualTo(5);
        assertThat(up.get("items").get(0).get("quantity").asInt()).isEqualTo(5);
        assertThat(up.get("total").get("amount").asLong()).isEqualTo(5_000);

        setQuantity(buyer, cartId, line, 1, 200);
        assertThat(available(ga)).isEqualTo(9);   // the difference went back at once
        assertThat(cartItemRepository.findById(line).orElseThrow().getHoldExpiresAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hold.truncatedTo(ChronoUnit.MILLIS));

        setQuantity(buyer, cartId, line, 1, 200);   // no change
        assertThat(available(ga)).isEqualTo(9);
        setQuantity(buyer, cartId, line, 0, 400);
        setQuantity(buyer, cartId, line, -2, 400);
        setQuantity(buyer, cartId, line, 7, 409);   // over the limit of 6 per order
        assertThat(available(ga)).isEqualTo(9);
    }

    @Test
    void raisingAboveWhatIsAvailable_isA409_andChangesNothing() throws Exception {
        UUID cartId = newCart(buyer);
        UUID line = lineOf(body(add(buyer, cartId, ga, 1, 201)), ga);
        jdbc.update("update ticket_types set quantity_available = 1 where id = ?", ga);   // someone else holds the rest

        setQuantity(buyer, cartId, line, 3, 409);   // needs 2 more, 1 is left

        assertThat(available(ga)).isEqualTo(1);
        assertThat(cartItemRepository.findById(line).orElseThrow().getQuantity()).isEqualTo(1);
        setQuantity(buyer, cartId, line, 2, 200);   // 1 more is there
        assertThat(available(ga)).isZero();
    }

    @Test
    void anExpiredHold_cannotBeChanged() throws Exception {
        UUID cartId = newCart(buyer);
        UUID line = lineOf(body(add(buyer, cartId, ga, 2, 201)), ga);
        jdbc.update("update cart_items set hold_expires_at = now() - interval '1 minute' where id = ?", line);

        setQuantity(buyer, cartId, line, 1, 409);

        assertThat(cartItemRepository.findById(line).orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test
    void onlyTheOwnerBuyerMayChangeALine_andAnUnknownLineIs404() throws Exception {
        UUID cartId = newCart(buyer);
        UUID line = lineOf(body(add(buyer, cartId, ga, 2, 201)), ga);

        setQuantity(otherBuyer, cartId, line, 3, 403);
        mockMvc.perform(patch("/api/v1/carts/{id}/items/{item}", cartId, line).contentType("application/json").content("{\"quantity\":3}"))
                .andExpect(status().isUnauthorized());
        setQuantity(buyer, cartId, UUID.randomUUID(), 3, 404);
        assertThat(cartItemRepository.findById(line).orElseThrow().getQuantity()).isEqualTo(2);
        assertThat(available(ga)).isEqualTo(8);
    }

    @Test
    void aChangeAndAnAddRacingForTheLastTicket_neverOversell() throws Exception {
        UUID cartOne = newCart(buyer);
        UUID cartTwo = newCart(otherBuyer);
        UUID line = lineOf(body(add(buyer, cartOne, ga, 1, 201)), ga);
        jdbc.update("update ticket_types set quantity_available = 1 where id = ?", ga);   // exactly one ticket left

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Integer> change = pool.submit(() -> {
                go.await();
                return mockMvc.perform(patch("/api/v1/carts/{id}/items/{item}", cartOne, line).header("Authorization", buyer)
                        .contentType("application/json").content("{\"quantity\":2}")).andReturn().getResponse().getStatus();
            });
            Future<Integer> addition = pool.submit(() -> {
                go.await();
                return mockMvc.perform(post("/api/v1/carts/{id}/items", cartTwo).header("Authorization", otherBuyer).contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ga + "\",\"quantity\":1}")).andReturn().getResponse().getStatus();
            });
            go.countDown();
            int a = change.get(30, TimeUnit.SECONDS);
            int b = addition.get(30, TimeUnit.SECONDS);

            assertThat(List.of(a, b)).containsExactlyInAnyOrder(200 == a ? 200 : 409, 200 == a ? 409 : 201);
            assertThat(available(ga)).isZero();   // never negative: the last ticket went to exactly one of them
        } finally {
            pool.shutdownNow();
        }
    }
}
