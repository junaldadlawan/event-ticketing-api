package com.junaldadlawan.event_ticketing_api.cart;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring and the
 * real Postgres pessimistic-locking behavior end to end for the cart module —
 * mirrors {@code TicketTypeAccessIntegrationTest}/{@code SeatMapAccessIntegrationTest}.
 * Covers: buyer-only access with NO admin/organizer bypass (a genuine
 * departure from Event/TicketType/Venue's pattern), hold placement/release for
 * both GA and reserved-seating ticket types, purchasability/sale-window/
 * single-event-per-cart gating, expired-hold reclaim, promo-code application,
 * and — the centerpiece — real concurrent requests for the last unit of GA
 * stock / the same seat, proving BR-INV-005/006 against actual DB row locks
 * rather than a mocked repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private SeatMapRepository seatMapRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private PromoCodeRepository promoCodeRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdCartItemIds = new ArrayList<>();
    private final List<UUID> createdCartIds = new ArrayList<>();
    private final List<UUID> createdPromoCodeIds = new ArrayList<>();
    private final List<UUID> createdSeatIds = new ArrayList<>();
    private final List<UUID> createdSeatMapIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdOrgIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdCartItemIds) {
            cartItemRepository.deleteById(id);
        }
        createdCartItemIds.clear();
        for (UUID id : createdCartIds) {
            cartRepository.deleteById(id);
        }
        createdCartIds.clear();
        for (UUID id : createdPromoCodeIds) {
            promoCodeRepository.deleteById(id);
        }
        createdPromoCodeIds.clear();
        for (UUID id : createdSeatIds) {
            seatRepository.deleteById(id);
        }
        createdSeatIds.clear();
        for (UUID id : createdSeatMapIds) {
            seatMapRepository.deleteById(id);
        }
        createdSeatMapIds.clear();
        for (UUID id : createdTicketTypeIds) {
            ticketTypeRepository.deleteById(id);
        }
        createdTicketTypeIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID id : createdOrgIds) {
            organizationRepository.deleteById(id);
        }
        createdOrgIds.clear();
    }

    // ---- fixtures ----

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .role(role)
                .build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Cart Access Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder()
                .userId(userId)
                .organizationId(organizationId)
                .build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Cart Access Test Event")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("C" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistGaTicketType(UUID eventId, int quantityAvailable) {
        TicketType tt = TicketType.builder()
                .eventId(eventId)
                .name("GA")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(quantityAvailable)
                .quantityAvailable(quantityAvailable)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistReservedTicketType(UUID eventId) {
        TicketType tt = TicketType.builder()
                .eventId(eventId)
                .name("VIP")
                .kind(TicketTypeKind.RESERVED_SEATING)
                .price(Money.builder().amount(5000L).currency("USD").build())
                .quantityTotal(50)
                .quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistSeatMap(UUID eventId) {
        SeatMap seatMap = SeatMap.builder().eventId(eventId).build();
        SeatMap saved = seatMapRepository.save(seatMap);
        createdSeatMapIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistSeat(UUID seatMapId, SeatStatus status) {
        Seat seat = Seat.builder().seatMapId(seatMapId).section("A").row("1").seatNumber(UUID.randomUUID().toString().substring(0, 4)).status(status).build();
        Seat saved = seatRepository.save(seat);
        createdSeatIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistPromoCode(UUID eventId, DiscountType type, BigDecimal value, Set<UUID> applicable, Instant validFrom, Instant validUntil) {
        PromoCode promoCode = PromoCode.builder()
                .eventId(eventId)
                .code("SAVE10")
                .discountType(type)
                .discountValue(value)
                .applicableTicketTypeIds(applicable)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .build();
        PromoCode saved = promoCodeRepository.save(promoCode);
        createdPromoCodeIds.add(saved.getId());
        return saved.getId();
    }

    private UUID createCart(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/carts").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        UUID cartId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdCartIds.add(cartId);
        return cartId;
    }

    private String addItemBody(UUID ticketTypeId, UUID seatId, Integer quantity) {
        StringBuilder sb = new StringBuilder("{\"ticketTypeId\":\"").append(ticketTypeId).append("\"");
        if (seatId != null) {
            sb.append(",\"seatId\":\"").append(seatId).append("\"");
        }
        if (quantity != null) {
            sb.append(",\"quantity\":").append(quantity);
        }
        sb.append("}");
        return sb.toString();
    }

    private void trackItemFromResponse(MvcResult result) throws Exception {
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        var items = json.get("items");
        if (items != null) {
            for (var item : items) {
                UUID itemId = UUID.fromString(item.get("id").asText());
                if (!createdCartItemIds.contains(itemId)) {
                    createdCartItemIds.add(itemId);
                }
            }
        }
    }

    // ---- buyer-only access, no admin/organizer bypass ----

    @Test
    void getCart_buyerOnly_strangerAndAdminBothForbidden() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        String buyerToken = jwtService.generateAccessToken(buyer);
        String strangerToken = jwtService.generateAccessToken(stranger);
        String adminToken = jwtService.generateAccessToken(admin);

        UUID cartId = createCart(buyerToken);

        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId.toString()));

        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // Key regression: unlike Event/TicketType/Venue, there is NO admin bypass for cart visibility.
        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCart_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/carts/{cartId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---- addItem() : GA and reserved-seating happy paths ----

    @Test
    void addItem_ga_success_decrementsAvailability() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        MvcResult result = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andReturn();
        trackItemFromResponse(result);

        TicketType updated = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        assertThat(updated.getQuantityAvailable()).isEqualTo(3);
    }

    @Test
    void addItem_reservedSeating_success_seatHeld() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.ON_SALE);
        UUID ticketTypeId = persistReservedTicketType(eventId);
        UUID seatMapId = persistSeatMap(eventId);
        UUID seatId = persistSeat(seatMapId, SeatStatus.AVAILABLE);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        MvcResult result = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, seatId, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].seatId").value(seatId.toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andReturn();
        trackItemFromResponse(result);

        Seat updatedSeat = seatRepository.findById(seatId).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void addItem_reservedSeating_missingSeatId_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.ON_SALE);
        UUID ticketTypeId = persistReservedTicketType(eventId);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_nonExistentTicketType_returns404() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(UUID.randomUUID(), null, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_draftEvent_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 1)))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_outsideSaleWindow_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.ON_SALE);
        TicketType tt = TicketType.builder()
                .eventId(eventId)
                .name("GA")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(5)
                .quantityAvailable(5)
                .saleStartAt(Instant.now().plus(1, ChronoUnit.DAYS)) // not on sale yet
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build();
        UUID ticketTypeId = ticketTypeRepository.save(tt).getId();
        createdTicketTypeIds.add(ticketTypeId);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 1)))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_differentEventThanExistingCartItems_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventOneId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID eventTwoId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeOneId = persistGaTicketType(eventOneId, 5);
        UUID ticketTypeTwoId = persistGaTicketType(eventTwoId, 5);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        MvcResult first = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeOneId, null, 1)))
                .andExpect(status().isCreated())
                .andReturn();
        trackItemFromResponse(first);

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeTwoId, null, 1)))
                .andExpect(status().isBadRequest());
    }

    // ---- removeItem() ----

    @Test
    void removeItem_ga_releasesHold_restoresAvailability() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        MvcResult addResult = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 2)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID itemId = UUID.fromString(objectMapper.readTree(addResult.getResponse().getContentAsString()).get("items").get(0).get("id").asText());
        assertThat(ticketTypeRepository.findById(ticketTypeId).orElseThrow().getQuantityAvailable()).isEqualTo(3);

        mockMvc.perform(delete("/api/v1/carts/{cartId}/items/{itemId}", cartId, itemId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNoContent());

        assertThat(ticketTypeRepository.findById(ticketTypeId).orElseThrow().getQuantityAvailable()).isEqualTo(5);
        assertThat(cartItemRepository.findById(itemId)).isEmpty();
    }

    @Test
    void removeItem_seat_releasesHold_seatBackToAvailable() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.ON_SALE);
        UUID ticketTypeId = persistReservedTicketType(eventId);
        UUID seatMapId = persistSeatMap(eventId);
        UUID seatId = persistSeat(seatMapId, SeatStatus.AVAILABLE);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        MvcResult addResult = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, seatId, null)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID itemId = UUID.fromString(objectMapper.readTree(addResult.getResponse().getContentAsString()).get("items").get(0).get("id").asText());

        mockMvc.perform(delete("/api/v1/carts/{cartId}/items/{itemId}", cartId, itemId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNoContent());

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(cartItemRepository.findById(itemId)).isEmpty();
    }

    @Test
    void removeItem_stranger_returns403() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);
        String strangerToken = jwtService.generateAccessToken(stranger);
        UUID cartId = createCart(buyerToken);

        mockMvc.perform(delete("/api/v1/carts/{cartId}/items/{itemId}", cartId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // ---- concurrency: the centerpiece proof of BR-INV-005/006 against real DB row locks ----

    @Test
    void concurrency_gaLastUnitOfStock_exactlyOneSucceeds() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyerA = inMemoryUser(Role.CUSTOMER);
        User buyerB = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        // Exactly ONE unit of GA stock available - both buyers race for it.
        UUID ticketTypeId = persistGaTicketType(eventId, 1);
        String tokenA = jwtService.generateAccessToken(buyerA);
        String tokenB = jwtService.generateAccessToken(buyerB);
        UUID cartA = createCart(tokenA);
        UUID cartB = createCart(tokenB);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> requestA = () -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartA)
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType("application/json")
                                .content(addItemBody(ticketTypeId, null, 1)))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> requestB = () -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartB)
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType("application/json")
                                .content(addItemBody(ticketTypeId, null, 1)))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> futureA = pool.submit(requestA);
            Future<Integer> futureB = pool.submit(requestB);
            startLatch.countDown();

            int statusA = futureA.get(15, TimeUnit.SECONDS);
            int statusB = futureB.get(15, TimeUnit.SECONDS);

            List<Integer> statuses = List.of(statusA, statusB);
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }

        // Final state: exactly the one unit was sold, never negative, never double-sold.
        TicketType finalTicketType = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        assertThat(finalTicketType.getQuantityAvailable()).isEqualTo(0);

        List<CartItem> itemsA = cartItemRepository.findByCartId(cartA);
        List<CartItem> itemsB = cartItemRepository.findByCartId(cartB);
        for (CartItem item : itemsA) {
            createdCartItemIds.add(item.getId());
        }
        for (CartItem item : itemsB) {
            createdCartItemIds.add(item.getId());
        }
        // Exactly one of the two carts holds the single item.
        assertThat(itemsA.size() + itemsB.size()).isEqualTo(1);
    }

    @Test
    void concurrency_sameSeat_exactlyOneSucceeds() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyerA = inMemoryUser(Role.CUSTOMER);
        User buyerB = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.ON_SALE);
        UUID ticketTypeId = persistReservedTicketType(eventId);
        UUID seatMapId = persistSeatMap(eventId);
        UUID seatId = persistSeat(seatMapId, SeatStatus.AVAILABLE);
        String tokenA = jwtService.generateAccessToken(buyerA);
        String tokenB = jwtService.generateAccessToken(buyerB);
        UUID cartA = createCart(tokenA);
        UUID cartB = createCart(tokenB);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> requestA = () -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartA)
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType("application/json")
                                .content(addItemBody(ticketTypeId, seatId, null)))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> requestB = () -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartB)
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType("application/json")
                                .content(addItemBody(ticketTypeId, seatId, null)))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> futureA = pool.submit(requestA);
            Future<Integer> futureB = pool.submit(requestB);
            startLatch.countDown();

            int statusA = futureA.get(15, TimeUnit.SECONDS);
            int statusB = futureB.get(15, TimeUnit.SECONDS);

            assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }

        Seat finalSeat = seatRepository.findById(seatId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.HELD);

        List<CartItem> itemsA = cartItemRepository.findByCartId(cartA);
        List<CartItem> itemsB = cartItemRepository.findByCartId(cartB);
        for (CartItem item : itemsA) {
            createdCartItemIds.add(item.getId());
        }
        for (CartItem item : itemsB) {
            createdCartItemIds.add(item.getId());
        }
        assertThat(itemsA.size() + itemsB.size()).isEqualTo(1);
    }

    // ---- expired-hold reclaim ----

    @Test
    void expiredHoldReclaim_ga_releasedAndReusedByDifferentBuyer() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User staleBuyer = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        // quantityAvailable is 0, as if fully consumed by the stale hold below.
        UUID ticketTypeId = persistGaTicketType(eventId, 0);
        String staleBuyerToken = jwtService.generateAccessToken(staleBuyer);
        UUID staleCartId = createCart(staleBuyerToken);
        CartItem staleItem = CartItem.builder()
                .cartId(staleCartId)
                .ticketTypeId(ticketTypeId)
                .seatId(null)
                .quantity(1)
                .holdExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
        UUID staleItemId = cartItemRepository.save(staleItem).getId();
        createdCartItemIds.add(staleItemId);

        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        MvcResult result = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 1)))
                .andExpect(status().isCreated())
                .andReturn();
        trackItemFromResponse(result);

        // The stale hold's row was deleted, and its released unit was reclaimed by this request.
        assertThat(cartItemRepository.findById(staleItemId)).isEmpty();
        assertThat(ticketTypeRepository.findById(ticketTypeId).orElseThrow().getQuantityAvailable()).isEqualTo(0);
    }

    @Test
    void expiredHoldReclaim_seat_releasedAndReusedByDifferentBuyer() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User staleBuyer = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.ON_SALE);
        UUID ticketTypeId = persistReservedTicketType(eventId);
        UUID seatMapId = persistSeatMap(eventId);
        // Seat is still HELD in the DB, but the hold that placed it there has expired.
        UUID seatId = persistSeat(seatMapId, SeatStatus.HELD);
        String staleBuyerToken = jwtService.generateAccessToken(staleBuyer);
        UUID staleCartId = createCart(staleBuyerToken);
        CartItem staleItem = CartItem.builder()
                .cartId(staleCartId)
                .ticketTypeId(ticketTypeId)
                .seatId(seatId)
                .quantity(1)
                .holdExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
        UUID staleItemId = cartItemRepository.save(staleItem).getId();
        createdCartItemIds.add(staleItemId);

        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        MvcResult result = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, seatId, null)))
                .andExpect(status().isCreated())
                .andReturn();
        trackItemFromResponse(result);

        assertThat(cartItemRepository.findById(staleItemId)).isEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD); // now held by the new buyer
    }

    // ---- applyPromoCode() / removePromoCode() ----

    @Test
    void applyPromoCode_percentageDiscount_endToEnd() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        persistPromoCode(eventId, DiscountType.PERCENTAGE, BigDecimal.valueOf(10), Set.of(),
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);
        MvcResult addResult = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 2))) // subtotal = 2000
                .andExpect(status().isCreated())
                .andReturn();
        trackItemFromResponse(addResult);

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode.code").value("SAVE10"))
                .andExpect(jsonPath("$.appliedPromoCode.discountAmount.amount").value(200))
                .andExpect(jsonPath("$.total.amount").value(1800));

        // removePromoCode() reverts the total.
        mockMvc.perform(delete("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode").doesNotExist())
                .andExpect(jsonPath("$.total.amount").value(2000));
    }

    @Test
    void applyPromoCode_emptyCart_returns422() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void applyPromoCode_expiredCode_returns422() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        persistPromoCode(eventId, DiscountType.FIXED, BigDecimal.valueOf(100), Set.of(),
                Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);
        MvcResult addResult = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 1)))
                .andExpect(status().isCreated())
                .andReturn();
        trackItemFromResponse(addResult);

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void applyPromoCode_notYetValidCode_returns422() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        persistPromoCode(eventId, DiscountType.FIXED, BigDecimal.valueOf(100), Set.of(),
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);
        MvcResult addResult = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 1)))
                .andExpect(status().isCreated())
                .andReturn();
        trackItemFromResponse(addResult);

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void applyPromoCode_inapplicableTicketType_returns422() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        // Restricted to some OTHER ticket type, not the one in the cart.
        persistPromoCode(eventId, DiscountType.FIXED, BigDecimal.valueOf(100), Set.of(UUID.randomUUID()),
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);
        MvcResult addResult = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content(addItemBody(ticketTypeId, null, 1)))
                .andExpect(status().isCreated())
                .andReturn();
        trackItemFromResponse(addResult);

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void promoCodeEndpoints_stranger_returns403() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);
        String strangerToken = jwtService.generateAccessToken(stranger);
        UUID cartId = createCart(buyerToken);

        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("""
                                {"code":"SAVE10"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }
}
