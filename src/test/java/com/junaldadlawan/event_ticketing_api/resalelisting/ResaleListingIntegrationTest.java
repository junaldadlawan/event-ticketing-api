package com.junaldadlawan.event_ticketing_api.resalelisting;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.resalelisting.repository.ResaleListingRepository;
import com.junaldadlawan.event_ticketing_api.resalelisting.repository.ResalePurchaseIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.resalepolicy.entity.ResalePolicy;
import com.junaldadlawan.event_ticketing_api.resalepolicy.enums.PriceCapRule;
import com.junaldadlawan.event_ticketing_api.resalepolicy.repository.ResalePolicyRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettransfer.entity.TicketTransfer;
import com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource;
import com.junaldadlawan.event_ticketing_api.tickettransfer.repository.TicketTransferRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for the resale-listing lifecycle
 * (create/cancel/browse/purchase) against real Postgres + real signed JWTs —
 * mirrors {@code CheckoutIntegrationTest}'s real-DB style since {@code
 * purchase} runs like a checkout. The dispatch's headline correctness
 * property for resale specifically: a successful purchase produces a real
 * {@code TicketTransfer} row ({@code source=RESALE}), reassigns {@code
 * owner_id}, and creates a real {@code Order}/{@code Payment} with {@code
 * payeeType=USER} — all verified by reading straight from Postgres, not just
 * trusting the response body.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResaleListingIntegrationTest {

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
    private TicketRepository ticketRepository;
    @Autowired
    private TicketTransferRepository ticketTransferRepository;
    @Autowired
    private ResalePolicyRepository resalePolicyRepository;
    @Autowired
    private ResaleListingRepository resaleListingRepository;
    @Autowired
    private ResalePurchaseIdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdPolicyIds = new ArrayList<>();
    private final List<UUID> createdListingIds = new ArrayList<>();
    private final List<UUID> createdIdempotencyKeyIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdPaymentIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdPaymentIds) {
            paymentRepository.deleteById(id);
        }
        createdPaymentIds.clear();
        for (UUID id : createdOrderIds) {
            orderRepository.deleteById(id);
        }
        createdOrderIds.clear();
        for (UUID id : createdIdempotencyKeyIds) {
            idempotencyKeyRepository.deleteById(id);
        }
        createdIdempotencyKeyIds.clear();
        for (UUID id : createdListingIds) {
            resaleListingRepository.deleteById(id);
        }
        createdListingIds.clear();
        for (UUID id : createdPolicyIds) {
            resalePolicyRepository.deleteById(id);
        }
        createdPolicyIds.clear();
        for (UUID ticketId : createdTicketIds) {
            for (TicketTransfer t : ticketTransferRepository.findByTicketIdOrderByTransferredAtAsc(ticketId)) {
                ticketTransferRepository.deleteById(t.getId());
            }
        }
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
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
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    // ---- fixtures ----

    private User persistUser(Role role) {
        User user = User.builder().name("Resale Test User").email("resale-" + UUID.randomUUID() + "@test.local")
                .passwordHash("irrelevant").role(role).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private User inMemoryUser(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Resale Listing Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Resale Listing Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("L" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTicketType(UUID eventId, long priceAmount) {
        TicketType tt = TicketType.builder()
                .eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(priceAmount).currency("USD").build())
                .quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private Ticket persistTicket(UUID eventId, UUID ticketTypeId, UUID ownerId) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(ticketTypeId).seatId(null)
                .ownerId(ownerId).ticketNumber("RSL-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("original-credential-" + ticketId).credentialVersion(0).status(TicketStatus.VALID)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
        return saved;
    }

    private void enableResale(UUID eventId, PriceCapRule rule, Long feeAmount) {
        ResalePolicy policy = ResalePolicy.builder()
                .eventId(eventId).enabled(true).priceCapRule(rule)
                .feeAmount(feeAmount != null ? Money.builder().amount(feeAmount).currency("USD").build() : null)
                .build();
        ResalePolicy saved = resalePolicyRepository.save(policy);
        createdPolicyIds.add(saved.getId());
    }

    private MvcResult createListing(String token, UUID ticketId, long amount, String currency) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":" + amount + ",\"currency\":\"" + currency + "\"}}"))
                .andReturn();
        return result;
    }

    private UUID trackListingIdFromResponse(MvcResult result) throws Exception {
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID listingId = UUID.fromString(json.get("id").asText());
        createdListingIds.add(listingId);
        return listingId;
    }

    // ---- create() ----

    @Test
    void create_owningBuyer_resaleEnabledWithinCap_returns201() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyer.getId());
        enableResale(eventId, PriceCapRule.FACE_VALUE, null);
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = createListing(buyerToken, ticket.getId(), 1000L, "USD");
        result.getResponse().setCharacterEncoding("UTF-8");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        trackListingIdFromResponse(result);
    }

    @Test
    void create_resaleDisabled_returns403() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyer.getId());
        // No resale policy row at all.
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = createListing(buyerToken, ticket.getId(), 1000L, "USD");
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void create_askingPriceAbovePriceCap_returns403() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyer.getId());
        enableResale(eventId, PriceCapRule.FACE_VALUE, null);
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = createListing(buyerToken, ticket.getId(), 5000L, "USD");
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void create_nonOwningCaller_returns403() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User stranger = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyer.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String strangerToken = jwtService.generateAccessToken(stranger);

        MvcResult result = createListing(strangerToken, ticket.getId(), 1000L, "USD");
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void create_ticketAlreadyHasActiveListing_returns409() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyer.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult first = createListing(buyerToken, ticket.getId(), 1000L, "USD");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        trackListingIdFromResponse(first);

        MvcResult second = createListing(buyerToken, ticket.getId(), 900L, "USD");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"USD\"}}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- cancel() ----

    @Test
    void cancel_owningSeller_returns204_thenNoLongerInActiveList() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyer.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String buyerToken = jwtService.generateAccessToken(buyer);
        MvcResult created = createListing(buyerToken, ticket.getId(), 1000L, "USD");
        UUID listingId = trackListingIdFromResponse(created);

        mockMvc.perform(delete("/api/v1/resale-listings/{listingId}", listingId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/events/{eventId}/resale-listings", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void cancel_nonSeller_returns403() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User stranger = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyer.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String buyerToken = jwtService.generateAccessToken(buyer);
        MvcResult created = createListing(buyerToken, ticket.getId(), 1000L, "USD");
        UUID listingId = trackListingIdFromResponse(created);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(delete("/api/v1/resale-listings/{listingId}", listingId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // ---- list active (public) ----

    @Test
    void listActive_public_returnsOnlyActiveListings() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket activeTicket = persistTicket(eventId, ticketTypeId, buyer.getId());
        Ticket cancelledTicket = persistTicket(eventId, ticketTypeId, buyer.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String buyerToken = jwtService.generateAccessToken(buyer);
        MvcResult activeResult = createListing(buyerToken, activeTicket.getId(), 1000L, "USD");
        UUID activeListingId = trackListingIdFromResponse(activeResult);
        MvcResult cancelledResult = createListing(buyerToken, cancelledTicket.getId(), 1000L, "USD");
        UUID cancelledListingId = trackListingIdFromResponse(cancelledResult);
        mockMvc.perform(delete("/api/v1/resale-listings/{listingId}", cancelledListingId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/events/{eventId}/resale-listings", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(activeListingId.toString()));
    }

    // ---- purchase() — the headline correctness test ----

    @Test
    void purchase_success_reassignsOwner_recordsResaleTransfer_createsOrderWithUserPayee() throws Exception {
        User seller = persistUser(Role.CUSTOMER);
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, seller.getId());
        String originalCredential = ticket.getCredential();
        enableResale(eventId, PriceCapRule.NONE, null);
        String sellerToken = jwtService.generateAccessToken(seller);
        MvcResult created = createListing(sellerToken, ticket.getId(), 1500L, "USD");
        UUID listingId = trackListingIdFromResponse(created);

        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID idempotencyKey = UUID.randomUUID();
        createdIdempotencyKeyIds.add(idempotencyKey);
        MvcResult purchaseResult = mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andReturn();
        assertThat(purchaseResult.getResponse().getStatus()).isEqualTo(201);
        var json = objectMapper.readTree(purchaseResult.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(json.get("id").asText());
        createdOrderIds.add(orderId);
        assertThat(json.get("payeeType").asText()).isEqualTo("USER");
        assertThat(json.get("payeeId").asText()).isEqualTo(seller.getId().toString());
        assertThat(json.get("buyerId").asText()).isEqualTo(buyer.getId().toString());
        assertThat(json.get("total").get("amount").asLong()).isEqualTo(1500L);
        assertThat(json.get("tickets")).hasSize(1);
        assertThat(json.get("tickets").get(0).get("ownerId").asText()).isEqualTo(buyer.getId().toString());
        assertThat(json.get("tickets").get(0).has("credential")).isFalse();

        // Real-DB verification: Order.
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getPayeeType()).isEqualTo(PayeeType.USER);
        assertThat(order.getPayeeId()).isEqualTo(seller.getId());
        assertThat(order.getBuyerId()).isEqualTo(buyer.getId());

        // Real-DB verification: Payment.
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        for (Payment p : payments) {
            createdPaymentIds.add(p.getId());
        }
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getAmount().getAmount()).isEqualTo(1500L);

        // Real-DB verification: Ticket ownership + credential invalidation (BR-TRANSFER-005).
        Ticket refreshedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedTicket.getOwnerId()).isEqualTo(buyer.getId());
        assertThat(refreshedTicket.getCredentialVersion()).isEqualTo(1);
        assertThat(refreshedTicket.getCredential()).isNotEqualTo(originalCredential);
        assertThat(refreshedTicket.getStatus()).isEqualTo(TicketStatus.VALID);

        // Real-DB verification: a genuine TicketTransfer audit row, source=RESALE.
        List<TicketTransfer> history = ticketTransferRepository.findByTicketIdOrderByTransferredAtAsc(ticket.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getSource()).isEqualTo(TransferSource.RESALE);
        assertThat(history.get(0).getFromUserId()).isEqualTo(seller.getId());
        assertThat(history.get(0).getToUserId()).isEqualTo(buyer.getId());

        // Real-DB verification: listing marked SOLD with buyerOrderId set.
        var listing = resaleListingRepository.findById(listingId).orElseThrow();
        assertThat(listing.getStatus().name()).isEqualTo("SOLD");
        assertThat(listing.getBuyerOrderId()).isEqualTo(orderId);
    }

    @Test
    void purchase_buyerIsSeller_returns403() throws Exception {
        User seller = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, seller.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String sellerToken = jwtService.generateAccessToken(seller);
        MvcResult created = createListing(sellerToken, ticket.getId(), 1000L, "USD");
        UUID listingId = trackListingIdFromResponse(created);

        UUID idempotencyKey = UUID.randomUUID();
        createdIdempotencyKeyIds.add(idempotencyKey);
        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchase_listingAlreadySold_returns409_onSecondBuyer() throws Exception {
        User seller = persistUser(Role.CUSTOMER);
        User buyerA = persistUser(Role.CUSTOMER);
        User buyerB = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, seller.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String sellerToken = jwtService.generateAccessToken(seller);
        MvcResult created = createListing(sellerToken, ticket.getId(), 1000L, "USD");
        UUID listingId = trackListingIdFromResponse(created);

        String buyerAToken = jwtService.generateAccessToken(buyerA);
        UUID keyA = UUID.randomUUID();
        createdIdempotencyKeyIds.add(keyA);
        MvcResult firstPurchase = mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Authorization", "Bearer " + buyerAToken)
                        .header("Idempotency-Key", keyA.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andReturn();
        assertThat(firstPurchase.getResponse().getStatus()).isEqualTo(201);
        UUID orderIdA = UUID.fromString(objectMapper.readTree(firstPurchase.getResponse().getContentAsString()).get("id").asText());
        createdOrderIds.add(orderIdA);
        for (Payment p : paymentRepository.findByOrderId(orderIdA)) {
            createdPaymentIds.add(p.getId());
        }

        String buyerBToken = jwtService.generateAccessToken(buyerB);
        UUID keyB = UUID.randomUUID();
        createdIdempotencyKeyIds.add(keyB);
        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Authorization", "Bearer " + buyerBToken)
                        .header("Idempotency-Key", keyB.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void purchase_paymentDeclined_returns402_listingStaysActiveForRetry() throws Exception {
        User seller = persistUser(Role.CUSTOMER);
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, seller.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String sellerToken = jwtService.generateAccessToken(seller);
        MvcResult created = createListing(sellerToken, ticket.getId(), 1000L, "USD");
        UUID listingId = trackListingIdFromResponse(created);

        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID idempotencyKey = UUID.randomUUID();
        createdIdempotencyKeyIds.add(idempotencyKey);
        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_fail\"}"))
                .andExpect(status().isPaymentRequired());

        var listing = resaleListingRepository.findById(listingId).orElseThrow();
        assertThat(listing.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(idempotencyKeyRepository.findById(idempotencyKey)).isEmpty();
        Ticket unchangedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(unchangedTicket.getOwnerId()).isEqualTo(seller.getId());
        assertThat(unchangedTicket.getCredentialVersion()).isEqualTo(0);
    }

    @Test
    void purchase_replaySameIdempotencyKey_returnsSameOrder_noDoubleCharge() throws Exception {
        User seller = persistUser(Role.CUSTOMER);
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 1000L);
        Ticket ticket = persistTicket(eventId, ticketTypeId, seller.getId());
        enableResale(eventId, PriceCapRule.NONE, null);
        String sellerToken = jwtService.generateAccessToken(seller);
        MvcResult created = createListing(sellerToken, ticket.getId(), 1000L, "USD");
        UUID listingId = trackListingIdFromResponse(created);

        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID idempotencyKey = UUID.randomUUID();
        createdIdempotencyKeyIds.add(idempotencyKey);
        MvcResult first = mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        UUID firstOrderId = UUID.fromString(objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText());
        createdOrderIds.add(firstOrderId);
        for (Payment p : paymentRepository.findByOrderId(firstOrderId)) {
            createdPaymentIds.add(p.getId());
        }

        MvcResult replay = mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andReturn();
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        UUID replayOrderId = UUID.fromString(objectMapper.readTree(replay.getResponse().getContentAsString()).get("id").asText());
        assertThat(replayOrderId).isEqualTo(firstOrderId);

        assertThat(paymentRepository.findByOrderId(firstOrderId)).hasSize(1);
        assertThat(ticketTransferRepository.findByTicketIdOrderByTransferredAtAsc(ticket.getId())).hasSize(1);
    }
}
