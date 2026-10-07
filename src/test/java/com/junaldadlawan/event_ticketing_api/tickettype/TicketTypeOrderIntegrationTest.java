package com.junaldadlawan.event_ticketing_api.tickettype;

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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
 * Real security chain + real Postgres (the V29 migration) for saving the organizer's arrangement of an
 * event's ticket types: new types land at the end, {@code PUT .../ticket-types/order} saves a drag-and-drop
 * arrangement, the list comes back in that order, and a stale or foreign list is refused without changing anything.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketTypeOrderIntegrationTest {

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
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdTicketTypeIds.forEach(ticketTypeRepository::deleteById);
        createdTicketTypeIds.clear();
        createdEventIds.forEach(eventRepository::deleteById);
        createdEventIds.clear();
        createdMembers.forEach(member -> organizationMemberRepository
                .findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        createdMembers.clear();
        createdOrgIds.forEach(organizationRepository::deleteById);
        createdOrgIds.clear();
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID eventOwnedBy(User owner) {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Ticket Order Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(owner.getId()).documents(List.of()).build());
        createdOrgIds.add(organization.getId());
        OrganizationMember member = OrganizationMember.builder().userId(owner.getId()).organizationId(organization.getId()).build();
        member.getRoles().add(OrganizationRole.OWNER);
        createdMembers.add(organizationMemberRepository.save(member));
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = eventRepository.save(Event.builder().organizationId(organization.getId()).title("Ticket Order Test Event")
                .description("desc").category("Music").status(EventStatus.DRAFT)
                .ticketPrefix("O" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build());
        createdEventIds.add(event.getId());
        return event.getId();
    }

    private UUID createTicketType(UUID eventId, String token, String name) throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        MvcResult result = mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","kind":"GENERAL_ADMISSION","price":{"amount":1000,"currency":"USD"},
                                "quantityTotal":50,"saleStartAt":"%s","saleEndAt":"%s"}
                                """.formatted(name, start, start.plus(5, ChronoUnit.DAYS))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdTicketTypeIds.add(id);
        return id;
    }

    private ResultActions putOrder(UUID eventId, String token, UUID... ids) throws Exception {
        StringBuilder body = new StringBuilder("{\"ticketTypeIds\":[");
        for (int i = 0; i < ids.length; i++) {
            body.append(i == 0 ? "" : ",").append('"').append(ids[i]).append('"');
        }
        body.append("]}");
        var request = put("/api/v1/events/{eventId}/ticket-types/order", eventId).contentType("application/json").content(body.toString());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private List<String> listedNames(UUID eventId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        List<String> names = new ArrayList<>();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            names.add(node.get("name").asText());
        }
        return names;
    }

    @Test
    void newTicketTypes_goToTheEnd_andTheListKeepsThatOrder() throws Exception {
        User owner = user(Role.CUSTOMER);
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        createTicketType(eventId, token, "First");
        createTicketType(eventId, token, "Second");
        createTicketType(eventId, token, "Third");

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("First")).andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].name").value("Second")).andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[2].name").value("Third")).andExpect(jsonPath("$[2].position").value(2));
    }

    @Test
    void reorder_savesTheDraggedArrangement_andItSurvivesReloading() throws Exception {
        User owner = user(Role.CUSTOMER);
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID first = createTicketType(eventId, token, "First");
        UUID second = createTicketType(eventId, token, "Second");
        UUID third = createTicketType(eventId, token, "Third");

        putOrder(eventId, token, third, first, second).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(third.toString())).andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].id").value(first.toString())).andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[2].id").value(second.toString())).andExpect(jsonPath("$[2].position").value(2));

        assertThat(listedNames(eventId, token)).containsExactly("Third", "First", "Second");
        mockMvc.perform(get("/api/v1/ticket-types/{id}", second).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.position").value(2));
        assertThat(ticketTypeRepository.findById(third).orElseThrow().getPosition()).isZero();
    }

    @Test
    void aTicketTypeCreatedAfterAReorder_goesBehindTheArrangement() throws Exception {
        User owner = user(Role.CUSTOMER);
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID a = createTicketType(eventId, token, "A");
        UUID b = createTicketType(eventId, token, "B");
        putOrder(eventId, token, b, a).andExpect(status().isOk());

        createTicketType(eventId, token, "C");

        assertThat(listedNames(eventId, token)).containsExactly("B", "A", "C");
    }

    @Test
    void aDeletedTicketType_dropsOut_andTheNextReorderClosesTheGap() throws Exception {
        User owner = user(Role.CUSTOMER);
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID a = createTicketType(eventId, token, "A");
        UUID b = createTicketType(eventId, token, "B");
        UUID c = createTicketType(eventId, token, "C");

        mockMvc.perform(delete("/api/v1/ticket-types/{id}", b).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(listedNames(eventId, token)).containsExactly("A", "C");

        // the deleted one is not part of the arrangement any more - listing it is refused, leaving it out works
        putOrder(eventId, token, a, b, c).andExpect(status().isBadRequest());
        putOrder(eventId, token, c, a).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(0)).andExpect(jsonPath("$[1].position").value(1));
        assertThat(listedNames(eventId, token)).containsExactly("C", "A");
    }

    @Test
    void reorder_aStaleOrForeignList_is400_andNothingChanges() throws Exception {
        User owner = user(Role.CUSTOMER);
        UUID eventId = eventOwnedBy(owner);
        UUID otherEventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID a = createTicketType(eventId, token, "A");
        UUID b = createTicketType(eventId, token, "B");
        UUID foreign = createTicketType(otherEventId, token, "Foreign");

        putOrder(eventId, token, a).andExpect(status().isBadRequest());                       // omits B
        putOrder(eventId, token, a, a, b).andExpect(status().isBadRequest());                 // repeats A
        putOrder(eventId, token, a, b, foreign).andExpect(status().isBadRequest());           // another event's type
        putOrder(eventId, token, b, UUID.randomUUID()).andExpect(status().isBadRequest());    // unknown id
        putOrder(eventId, token).andExpect(status().isBadRequest());                          // empty

        assertThat(listedNames(eventId, token)).containsExactly("A", "B");
        assertThat(listedNames(otherEventId, token)).containsExactly("Foreign");
    }

    @Test
    void reorder_accessControl() throws Exception {
        User owner = user(Role.CUSTOMER);
        User stranger = user(Role.CUSTOMER);
        User admin = user(Role.ADMIN);
        UUID eventId = eventOwnedBy(owner);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID a = createTicketType(eventId, ownerToken, "A");
        UUID b = createTicketType(eventId, ownerToken, "B");

        putOrder(eventId, null, b, a).andExpect(status().isUnauthorized());
        putOrder(eventId, jwtService.generateAccessToken(stranger), b, a).andExpect(status().isForbidden());
        assertThat(listedNames(eventId, ownerToken)).containsExactly("A", "B");

        putOrder(eventId, jwtService.generateAccessToken(admin), b, a).andExpect(status().isOk());
        assertThat(listedNames(eventId, ownerToken)).containsExactly("B", "A");
        putOrder(UUID.randomUUID(), ownerToken, a).andExpect(status().isNotFound());
    }
}
