package com.junaldadlawan.event_ticketing_api.order;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring for the
 * four order/ticket retrieval endpoints (Phase 6a) — mirrors {@code
 * TicketAccessIntegrationTest}/{@code TicketTypeAccessIntegrationTest}'s
 * style. Covers BR-CART-004's visibility scoping for {@code GET
 * /orders/{orderId}}, {@code GET /orders/{orderId}/tickets}, and {@code GET
 * /users/me/orders} (all buyer/organizer/owner/admin-visible), plus the
 * deliberately DIFFERENT, organizer-only rule for {@code GET
 * /events/{eventId}/orders} — where the order's own buyer must be forbidden
 * unless they're also that event's organizer/admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderAccessIntegrationTest {

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
    private OrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
        for (UUID id : createdOrderIds) {
            orderRepository.deleteById(id);
        }
        createdOrderIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID orgId : createdOrgIds) {
            organizationRepository.deleteById(orgId);
        }
        createdOrgIds.clear();
    }

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .role(role)
                .build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Order Access Test Org " + UUID.randomUUID())
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

    private UUID persistEvent(UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Order Access Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("O" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistOrder(UUID buyerId, UUID payeeOrgId) {
        Order order = Order.builder()
                .buyerId(buyerId)
                .payeeType(PayeeType.ORGANIZATION)
                .payeeId(payeeOrgId)
                .status(OrderStatus.PAID)
                .total(Money.builder().amount(1000L).currency("USD").build())
                .createdBy(buyerId.toString())
                .build();
        Order saved = orderRepository.save(order);
        createdOrderIds.add(saved.getId());
        return saved.getId();
    }

    private void persistTicket(UUID orderId, UUID eventId, UUID ownerId) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .orderId(orderId)
                .eventId(eventId)
                .ticketTypeId(UUID.randomUUID())
                .ownerId(ownerId)
                .ticketNumber("ORD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("test-credential-" + ticketId)
                .status(TicketStatus.VALID)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
    }

    // ---- GET /orders/{orderId} ----

    @Test
    void getOrder_owningBuyer_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.tickets.length()").value(1))
                .andExpect(jsonPath("$.tickets[0].credential").doesNotExist());
    }

    @Test
    void getOrder_eventOrganizer_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String organizerToken = jwtService.generateAccessToken(organizer);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getOrder_admin_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getOrder_stranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    /** Key regression class: a DIFFERENT org's organizer, not just a roleless stranger. */
    @Test
    void getOrder_crossOrgOrganizer_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrder_unknownOrder_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---- GET /orders/{orderId}/tickets ----

    @Test
    void getOrderTickets_owningBuyer_returns200_withoutCredential() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(get("/api/v1/orders/{orderId}/tickets", orderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].credential").doesNotExist());
    }

    /** Same cross-org-organizer-403 requirement as every other retrieval endpoint. */
    @Test
    void getOrderTickets_crossOrgOrganizer_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        User otherOrgOrganizer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOrganizer.getId());
        grantOrgRole(otherOrgOrganizer.getId(), otherOrgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String otherOrgOrganizerToken = jwtService.generateAccessToken(otherOrgOrganizer);

        mockMvc.perform(get("/api/v1/orders/{orderId}/tickets", orderId)
                        .header("Authorization", "Bearer " + otherOrgOrganizerToken))
                .andExpect(status().isForbidden());
    }

    // ---- GET /users/me/orders : self-scoped, no cross-user leakage ----

    @Test
    void listMyOrders_returnsOnlyCallersOwnOrders_neverAnotherBuyersOrder() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyerA = inMemoryUser(Role.CUSTOMER);
        User buyerB = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderA = persistOrder(buyerA.getId(), orgId);
        UUID orderB = persistOrder(buyerB.getId(), orgId);
        persistTicket(orderA, eventId, buyerA.getId());
        persistTicket(orderB, eventId, buyerB.getId());
        String tokenA = jwtService.generateAccessToken(buyerA);

        mockMvc.perform(get("/api/v1/users/me/orders")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + orderA + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id=='" + orderB + "')]").doesNotExist());
    }

    @Test
    void listMyOrders_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/orders"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /events/{eventId}/orders : organizer/owner/admin ONLY, deliberately not buyer-scoped ----

    @Test
    void listEventOrders_eventOwner_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + orderId + "')]").exists());
    }

    @Test
    void listEventOrders_admin_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    /**
     * The dispatch's explicitly-called-out critical scenario: the order's
     * OWN BUYER, who has no organizer/owner role on the event's
     * organization, must get 403 here — this endpoint is NOT buyer-scoped,
     * unlike {@code GET /orders/{orderId}}.
     */
    @Test
    void listEventOrders_ordersOwnBuyerWithoutOrganizerRole_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID orderId = persistOrder(buyer.getId(), orgId);
        persistTicket(orderId, eventId, buyer.getId());
        // The buyer has no OrganizationMember row at all for this org.
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", eventId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listEventOrders_crossOrgOwner_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listEventOrders_unknownEvent_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
