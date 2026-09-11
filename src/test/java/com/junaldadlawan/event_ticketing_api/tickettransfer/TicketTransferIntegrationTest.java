package com.junaldadlawan.event_ticketing_api.tickettransfer;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettransfer.repository.TicketTransferRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code POST
 * /tickets/{ticketId}/transfer} and {@code GET /tickets/{ticketId}/transfers}
 * against real Postgres + real signed JWTs — mirrors {@code
 * TicketAccessIntegrationTest}'s style. The dispatch's two headline
 * security/correctness properties for direct transfer:
 * <ol>
 *   <li>BR-TRANSFER-005 — the credential genuinely changes after a transfer,
 *   proven by reading {@code tickets.credential}/{@code credential_version}
 *   directly from Postgres before and after (not just trusting the response
 *   body).</li>
 *   <li>Only {@code ownerId}/{@code credential}/{@code credentialVersion}
 *   change — {@code ticketNumber}/{@code eventId}/{@code ticketTypeId}/
 *   {@code seatId}/{@code status} are untouched.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketTransferIntegrationTest {

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
    private TicketRepository ticketRepository;
    @Autowired
    private TicketTransferRepository ticketTransferRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        ticketTransferRepository.findAll().stream()
                .filter(t -> createdTicketIds.contains(t.getTicketId()))
                .forEach(t -> ticketTransferRepository.deleteById(t.getId()));
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
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

    private User inMemoryUser(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private User persistUser(Role role) {
        User user = User.builder()
                .name("Transfer Test User")
                .email("transfer-" + UUID.randomUUID() + "@test.local")
                .passwordHash("irrelevant")
                .role(role)
                .build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Transfer Test Org " + UUID.randomUUID())
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
                .title("Transfer Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("X" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private Ticket persistTicket(UUID eventId, UUID ownerId, TicketStatus status) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(UUID.randomUUID()).seatId(null)
                .ownerId(ownerId).ticketNumber("TRF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("original-credential-" + ticketId).credentialVersion(0).status(status)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
        return saved;
    }

    // ---- POST /tickets/{ticketId}/transfer ----

    @Test
    void transfer_owningBuyer_toRegisteredRecipient_returns200_andGenuinelyInvalidatesCredentialInDb() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User recipient = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String originalCredential = ticket.getCredential();
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + recipient.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(recipient.getId().toString()))
                .andExpect(jsonPath("$.credential").doesNotExist())
                // Unchanged fields.
                .andExpect(jsonPath("$.ticketNumber").value(ticket.getTicketNumber()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("VALID"));

        Ticket refreshed = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshed.getOwnerId()).isEqualTo(recipient.getId());
        assertThat(refreshed.getCredentialVersion()).isEqualTo(1);
        assertThat(refreshed.getCredential())
                .as("BR-TRANSFER-005: the credential must genuinely differ after a transfer")
                .isNotEqualTo(originalCredential);
        // Untouched fields, read fresh from the DB.
        assertThat(refreshed.getTicketTypeId()).isEqualTo(ticket.getTicketTypeId());
        assertThat(refreshed.getSeatId()).isEqualTo(ticket.getSeatId());
        assertThat(refreshed.getEventId()).isEqualTo(eventId);
        assertThat(refreshed.getStatus()).isEqualTo(TicketStatus.VALID);

        List<com.junaldadlawan.event_ticketing_api.tickettransfer.entity.TicketTransfer> history =
                ticketTransferRepository.findByTicketIdOrderByTransferredAtAsc(ticket.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromUserId()).isEqualTo(buyer.getId());
        assertThat(history.get(0).getToUserId()).isEqualTo(recipient.getId());
        assertThat(history.get(0).getSource()).isEqualTo(com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource.DIRECT_TRANSFER);
    }

    @Test
    void transfer_nonOwningCaller_returns403_ticketUnchanged() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User stranger = persistUser(Role.CUSTOMER);
        User recipient = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + recipient.getId() + "\"}"))
                .andExpect(status().isForbidden());

        Ticket refreshed = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshed.getOwnerId()).isEqualTo(buyer.getId());
        assertThat(refreshed.getCredentialVersion()).isEqualTo(0);
    }

    @Test
    void transfer_recipientNotRegistered_returns404() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_toSelf_returns400() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + buyer.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_ticketAlreadyUsed_returns409() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User recipient = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.USED);
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + recipient.getId() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void transfer_unknownTicket_returns404() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", UUID.randomUUID())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /tickets/{ticketId}/transfers ----

    @Test
    void listTransfers_owningBuyerAfterTransfer_returns200_withHistory() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User recipient = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String buyerToken = jwtService.generateAccessToken(buyer);
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + recipient.getId() + "\"}"))
                .andExpect(status().isOk());

        String recipientToken = jwtService.generateAccessToken(recipient);
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/transfers", ticket.getId())
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromUserId").value(buyer.getId().toString()))
                .andExpect(jsonPath("$[0].toUserId").value(recipient.getId().toString()))
                .andExpect(jsonPath("$[0].source").value("DIRECT_TRANSFER"));
    }

    @Test
    void listTransfers_eventOrganizer_returns200() throws Exception {
        User owner = persistUser(Role.CUSTOMER);
        User organizer = persistUser(Role.CUSTOMER);
        User buyer = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String organizerToken = jwtService.generateAccessToken(organizer);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/transfers", ticket.getId())
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());
    }

    @Test
    void listTransfers_roselessStranger_returns403() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User stranger = persistUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/transfers", ticket.getId())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTransfers_admin_returns200() throws Exception {
        User buyer = persistUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        Ticket ticket = persistTicket(eventId, buyer.getId(), TicketStatus.VALID);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/transfers", ticket.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
