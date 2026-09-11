package com.junaldadlawan.event_ticketing_api.refundpolicy;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
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
import com.junaldadlawan.event_ticketing_api.refundpolicy.repository.RefundPolicyRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code GET/PATCH
 * /events/{eventId}/refund-policy} against real Postgres + real signed JWTs.
 * Unlike {@code ResalePolicyIntegrationTest}'s public GET, this endpoint's
 * GET is deliberately NOT public (new {@code SecurityConfig} matcher) — this
 * proves the anonymous-401-vs-stranger-403 distinction plus the full
 * visibility matrix: organizer/owner, admin, and a buyer with a ticket on
 * the event but no organization role at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefundPolicyIntegrationTest {

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
    private RefundPolicyRepository refundPolicyRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdPolicyIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdPolicyIds) {
            refundPolicyRepository.deleteById(id);
        }
        createdPolicyIds.clear();
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
    }

    private User inMemoryUser(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Refund Policy Test Org " + UUID.randomUUID())
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
                .title("Refund Policy Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("F" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTicketType(UUID eventId) {
        TicketType tt = TicketType.builder()
                .eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private void persistTicket(UUID eventId, UUID ticketTypeId, UUID ownerId) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(ticketTypeId).seatId(null)
                .ownerId(ownerId).ticketNumber("F-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("cred-" + ticketId).credentialVersion(0).status(TicketStatus.VALID)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
    }

    private void trackPolicyForEvent(UUID eventId) {
        refundPolicyRepository.findByEventId(eventId).ifPresent(p -> createdPolicyIds.add(p.getId()));
    }

    // ---- GET: not public — anonymous 401 vs stranger 403 ----

    @Test
    void get_noTokenAtAll_returns401() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_roselessStrangerWithNoTicketAndNoOrgRole_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_buyerWithTicketOnEvent_noOrgRoleAtAll_returns200_withNoRefundsDefault() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistTicket(eventId, ticketTypeId, buyer.getId());
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.ruleType").value("NO_REFUNDS"));
    }

    @Test
    void get_organizer_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        String organizerToken = jwtService.generateAccessToken(organizer);

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());
    }

    @Test
    void get_admin_returns200_withNoOrganizationMembershipAtAll() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ---- PATCH authorization matrix ----

    @Test
    void update_owner_returns200_setsRefundableUntilNDays_roundTripGetReflectsIt() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"REFUNDABLE_UNTIL_N_DAYS\",\"daysBeforeEvent\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleType").value("REFUNDABLE_UNTIL_N_DAYS"))
                .andExpect(jsonPath("$.daysBeforeEvent").value(7));
        trackPolicyForEvent(eventId);

        mockMvc.perform(get("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleType").value("REFUNDABLE_UNTIL_N_DAYS"))
                .andExpect(jsonPath("$.daysBeforeEvent").value(7));
    }

    @Test
    void update_organizer_returns200_setsCustomTerms() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        String organizerToken = jwtService.generateAccessToken(organizer);

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"CUSTOM\",\"customTerms\":\"Store credit only, case-by-case.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleType").value("CUSTOM"))
                .andExpect(jsonPath("$.customTerms").value("Store credit only, case-by-case."));
        trackPolicyForEvent(eventId);
    }

    @Test
    void update_admin_returns200_withNoOrganizationMembershipAtAll() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"NO_REFUNDS\"}"))
                .andExpect(status().isOk());
        trackPolicyForEvent(eventId);
    }

    @Test
    void update_roselessStranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"NO_REFUNDS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_crossOrgOwner_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"NO_REFUNDS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"ruleType\":\"NO_REFUNDS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/refund-policy", UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"ruleType\":\"NO_REFUNDS\"}"))
                .andExpect(status().isNotFound());
    }
}
