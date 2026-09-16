package com.junaldadlawan.event_ticketing_api.analytics;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code GET /events/{eventId}/analytics}
 * and {@code GET /analytics/platform} against real Postgres + real signed
 * JWTs + the real SecurityConfig filter chain (BR-ANALYTICS-001/002).
 * Mirrors {@code RefundIntegrationTest}'s fixture style - persists real
 * Order/Ticket/TicketType rows directly (no checkout precedent needed;
 * analytics' own read-side aggregation is what's under test) and verifies
 * the response numbers against those seeded rows, covering all four
 * scenarios in {@code testing/analytics-test-plan.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsIntegrationTest {

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
    private OrderRepository orderRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();

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
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Analytics Test Org " + UUID.randomUUID())
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
        Instant startAt = Instant.now().plus(20, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Analytics Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("A" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTicketType(UUID eventId, int quantityTotal, int quantityAvailable) {
        TicketType tt = TicketType.builder()
                .eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(quantityTotal).quantityAvailable(quantityAvailable)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private Order persistOrder(UUID buyerId, UUID orgId, long totalAmount) {
        Order order = Order.builder()
                .buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(orgId)
                .status(OrderStatus.PAID).total(Money.builder().amount(totalAmount).currency("USD").build())
                .createdBy(buyerId.toString())
                .build();
        Order saved = orderRepository.save(order);
        createdOrderIds.add(saved.getId());
        return saved;
    }

    private void persistTicket(UUID eventId, UUID ticketTypeId, UUID ownerId, UUID orderId) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId).orderId(orderId).eventId(eventId).ticketTypeId(ticketTypeId).seatId(null)
                .ownerId(ownerId).ticketNumber("A-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("cred-" + ticketId).credentialVersion(0).status(TicketStatus.VALID)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
    }

    // ---- GET /events/{eventId}/analytics ----

    @Test
    void getEventAnalytics_owningOrganizer_numbersMatchSeededData() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId, 10, 8);
        Order order = persistOrder(buyerId, orgId, 2000L);
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(get("/api/v1/events/{eventId}/analytics", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.ticketsSold").value(2))
                .andExpect(jsonPath("$.revenue.amount").value(2000))
                .andExpect(jsonPath("$.revenue.currency").value("USD"))
                .andExpect(jsonPath("$.remainingInventory").value(8))
                .andExpect(jsonPath("$.salesOverTime[0].ticketsSold").value(2))
                .andExpect(jsonPath("$.salesOverTime[0].revenue.amount").value(2000));
    }

    @Test
    void getEventAnalytics_crossOrgOrganizer_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        mockMvc.perform(get("/api/v1/events/{eventId}/analytics", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEventAnalytics_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventId}/analytics", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEventAnalytics_unknownEvent_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(owner);

        mockMvc.perform(get("/api/v1/events/{eventId}/analytics", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---- GET /analytics/platform ----

    @Test
    void getPlatformAnalytics_admin_returns200() throws Exception {
        User admin = inMemoryUser(Role.ADMIN);
        String token = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/analytics/platform").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGmv").exists())
                .andExpect(jsonPath("$.activeOrganizers").exists())
                .andExpect(jsonPath("$.eventVolume").exists());
    }

    @Test
    void getPlatformAnalytics_nonAdmin_returns403() throws Exception {
        User customer = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(customer);

        mockMvc.perform(get("/api/v1/analytics/platform").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPlatformAnalytics_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/platform"))
                .andExpect(status().isUnauthorized());
    }
}
