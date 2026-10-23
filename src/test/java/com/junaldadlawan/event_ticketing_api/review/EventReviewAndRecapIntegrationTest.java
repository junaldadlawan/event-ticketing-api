package com.junaldadlawan.event_ticketing_api.review;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-REVIEW-001 (who may review, one per user, comments private) and BR-EVENT-RECAP-001 (after the end, EVENTS only). */
@SpringBootTest
@AutoConfigureMockMvc
class EventReviewAndRecapIntegrationTest {

    private static final String SECRET_COMMENT = "The queue at gate B was far too long";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketRepository ticketRepository;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> memberIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketIds = new ArrayList<>();
    private UUID orgId;
    private UUID endedEvent;
    private UUID upcomingEvent;
    private User owner;
    private User eventsOrganizer;
    private User analyticsOrganizer;
    private User admin;
    private User attendee;
    private User validHolder;
    private User stranger;

    @BeforeEach
    void setUp() {
        owner = persistUser("Olive", "Owner", Role.CUSTOMER);
        eventsOrganizer = persistUser("Evan", "Events", Role.CUSTOMER);
        analyticsOrganizer = persistUser("Ana", "Lytics", Role.CUSTOMER);
        admin = persistUser("Adam", "Admin", Role.ADMIN);
        attendee = persistUser("Rita", "Reviewer", Role.CUSTOMER);
        validHolder = persistUser("Vic", "Valid", Role.CUSTOMER);
        stranger = persistUser("Stan", "Stranger", Role.CUSTOMER);
        orgId = organizationRepository.save(Organization.builder().name("Review Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(owner.getId()).documents(List.of()).build()).getId();
        member(owner, OrganizationRole.OWNER, Set.of());
        member(eventsOrganizer, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.EVENTS));
        member(analyticsOrganizer, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.ANALYTICS));
        endedEvent = event(Instant.now().minus(2, ChronoUnit.DAYS), EventStatus.COMPLETED);
        upcomingEvent = event(Instant.now().plus(10, ChronoUnit.DAYS), EventStatus.PUBLISHED);
        ticket(endedEvent, attendee, TicketStatus.USED);
        ticket(endedEvent, validHolder, TicketStatus.VALID);
        ticket(upcomingEvent, attendee, TicketStatus.USED);
    }

    @AfterEach
    void tearDown() {
        eventIds.forEach(id -> {
            jdbc.update("delete from event_reviews where event_id = ?", id);
            jdbc.update("delete from event_recap_messages where event_id = ?", id);
            jdbc.update("delete from audit_log_entries where event_id = ?", id);
        });
        ticketIds.forEach(ticketRepository::deleteById);
        eventIds.forEach(eventRepository::deleteById);
        memberIds.forEach(id -> organizationMemberRepository.findByUserIdAndOrganizationId(id, orgId).ifPresent(organizationMemberRepository::delete));
        organizationRepository.deleteById(orgId);
        userIds.forEach(userRepository::deleteById);
    }

    // ---- reviews ----

    @Test
    void onlyAnAttendeeWithAUsedTicket_mayReview_andOnlyAfterTheEventEnded() throws Exception {
        review(post("/api/v1/events/{id}/reviews", endedEvent), validHolder, 4, null).andExpect(status().isForbidden());
        review(post("/api/v1/events/{id}/reviews", endedEvent), stranger, 4, null).andExpect(status().isForbidden());
        review(post("/api/v1/events/{id}/reviews", upcomingEvent), attendee, 4, null).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/events/{id}/reviews", endedEvent).contentType("application/json").content("{\"rating\":4}"))
                .andExpect(status().isUnauthorized());

        review(post("/api/v1/events/{id}/reviews", endedEvent), attendee, 6, null).andExpect(status().isBadRequest());
        review(post("/api/v1/events/{id}/reviews", endedEvent), attendee, 4, "<b>great</b>").andExpect(status().isBadRequest());
        review(post("/api/v1/events/{id}/reviews", endedEvent), attendee, 4, "Lovely night").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4)).andExpect(jsonPath("$.comment").value("Lovely night"));
    }

    @Test
    void oneReviewPerUser_editableAndDeletableByItsAuthor() throws Exception {
        review(post("/api/v1/events/{id}/reviews", endedEvent), attendee, 3, null).andExpect(status().isCreated());
        review(post("/api/v1/events/{id}/reviews", endedEvent), attendee, 5, null).andExpect(status().isConflict());

        review(put("/api/v1/events/{id}/reviews/me", endedEvent), attendee, 5, "Better than I thought").andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));
        as(get("/api/v1/events/{id}/reviews/me", endedEvent), attendee).andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5)).andExpect(jsonPath("$.comment").value("Better than I thought"));
        as(get("/api/v1/events/{id}/reviews/me", endedEvent), stranger).andExpect(status().isNotFound());
        review(put("/api/v1/events/{id}/reviews/me", endedEvent), stranger, 1, null).andExpect(status().isForbidden());

        as(delete("/api/v1/events/{id}/reviews/me", endedEvent), attendee).andExpect(status().isNoContent());
        as(get("/api/v1/events/{id}/reviews/me", endedEvent), attendee).andExpect(status().isNotFound());
        review(post("/api/v1/events/{id}/reviews", endedEvent), attendee, 2, null).andExpect(status().isCreated());

        assertThat(jdbc.queryForList("select action from audit_log_entries where event_id = ? and action like 'review.%' order by created_at",
                String.class, endedEvent)).containsExactly("review.created", "review.updated", "review.deleted", "review.created");
    }

    @Test
    void commentsAreOnlyForTheOwner_analyticsOrganizers_andAdmins() throws Exception {
        review(post("/api/v1/events/{id}/reviews", endedEvent), attendee, 4, SECRET_COMMENT).andExpect(status().isCreated());

        String summary = mockMvc.perform(get("/api/v1/events/{id}/reviews/summary", endedEvent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0)).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.counts.4").value(1)).andExpect(jsonPath("$.counts.1").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(summary).doesNotContain(SECRET_COMMENT).doesNotContain("Rita");

        for (User reader : List.of(owner, analyticsOrganizer, admin)) {
            as(get("/api/v1/events/{id}/reviews", endedEvent), reader).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].comment").value(SECRET_COMMENT))
                    .andExpect(jsonPath("$.content[0].reviewerName").value("Rita Reviewer"))
                    .andExpect(jsonPath("$.content[0].rating").value(4));
        }
        for (User outsider : List.of(eventsOrganizer, attendee, stranger)) {
            as(get("/api/v1/events/{id}/reviews", endedEvent), outsider).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/events/{id}/reviews", endedEvent)).andExpect(status().isUnauthorized());

        String activity = String.join("\n", jdbc.queryForList("select coalesce(details::text, '') from audit_log_entries where event_id = ?",
                String.class, endedEvent));
        assertThat(activity).doesNotContain(SECRET_COMMENT);
    }

    // ---- recap ----

    @Test
    void recapOpensOnlyAfterTheEnd_forTheOwnerAndEventsOrganizers() throws Exception {
        recap(post("/api/v1/events/{id}/recap", upcomingEvent), owner, "Too early", null).andExpect(status().isConflict());

        recap(post("/api/v1/events/{id}/recap", endedEvent), owner, "Thank you all for coming!", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Olive Owner"));
        recap(post("/api/v1/events/{id}/recap", endedEvent), eventsOrganizer, "See you next year", null).andExpect(status().isCreated());
        for (User notAllowed : List.of(analyticsOrganizer, attendee, stranger, admin)) {
            recap(post("/api/v1/events/{id}/recap", endedEvent), notAllowed, "Hi", null).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/events/{id}/recap", endedEvent).contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        // public, oldest first, with author and organization
        mockMvc.perform(get("/api/v1/events/{id}/recap", endedEvent)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].body").value(contains("Thank you all for coming!", "See you next year")))
                .andExpect(jsonPath("$[1].authorName").value("Evan Events"))
                .andExpect(jsonPath("$[1].organizationName").value(organizationRepository.findById(orgId).orElseThrow().getName()));
    }

    @Test
    void aMessageNeedsTextOrAnOwnUpload_andIsEditedAndDeletedByTheSamePeople_orAnAdmin() throws Exception {
        recap(post("/api/v1/events/{id}/recap", endedEvent), owner, "  ", null).andExpect(status().isBadRequest());
        recap(post("/api/v1/events/{id}/recap", endedEvent), owner, "Look", "https://elsewhere.example/cat.png").andExpect(status().isBadRequest());
        recap(post("/api/v1/events/{id}/recap", endedEvent), owner, "<script>x</script>", null).andExpect(status().isBadRequest());

        UUID first = id(recap(post("/api/v1/events/{id}/recap", endedEvent), owner, "First draft", null).andExpect(status().isCreated()));
        UUID second = id(recap(post("/api/v1/events/{id}/recap", endedEvent), owner, "Second", null).andExpect(status().isCreated()));

        recap(patch("/api/v1/events/{id}/recap/{m}", endedEvent, first), eventsOrganizer, "Final words", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Final words"));
        recap(patch("/api/v1/events/{id}/recap/{m}", endedEvent, first), owner, "", null).andExpect(status().isBadRequest());
        recap(patch("/api/v1/events/{id}/recap/{m}", endedEvent, first), analyticsOrganizer, "Nope", null).andExpect(status().isForbidden());
        recap(patch("/api/v1/events/{id}/recap/{m}", endedEvent, first), admin, "Nope", null).andExpect(status().isForbidden());

        as(delete("/api/v1/events/{id}/recap/{m}", endedEvent, second), stranger).andExpect(status().isForbidden());
        as(delete("/api/v1/events/{id}/recap/{m}", endedEvent, second), admin).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/events/{id}/recap", endedEvent))
                .andExpect(jsonPath("$[*].body").value(contains("Final words")));

        assertThat(jdbc.queryForList("select action from audit_log_entries where event_id = ? and action like 'recap_message.%' order by created_at",
                String.class, endedEvent)).containsExactly("recap_message.created", "recap_message.created",
                "recap_message.updated", "recap_message.deleted");
    }

    @Test
    void anUnpublishedEventHasNoPublicRecap() throws Exception {
        UUID draft = event(Instant.now().minus(2, ChronoUnit.DAYS), EventStatus.DRAFT);
        mockMvc.perform(get("/api/v1/events/{id}/recap", draft)).andExpect(status().isNotFound());
        as(get("/api/v1/events/{id}/recap", draft), owner).andExpect(status().isOk());
    }

    // ---- helpers ----

    private ResultActions review(MockHttpServletRequestBuilder request, User caller, int rating, String comment) throws Exception {
        String body = "{\"rating\":" + rating + (comment == null ? "" : ",\"comment\":" + objectMapper.writeValueAsString(comment)) + "}";
        return as(request.contentType("application/json").content(body), caller);
    }

    private ResultActions recap(MockHttpServletRequestBuilder request, User caller, String body, String imageUrl) throws Exception {
        String json = "{\"body\":" + objectMapper.writeValueAsString(body)
                + (imageUrl == null ? "" : ",\"imageUrl\":" + objectMapper.writeValueAsString(imageUrl)) + "}";
        return as(request.contentType("application/json").content(json), caller);
    }

    private ResultActions as(MockHttpServletRequestBuilder request, User caller) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.generateAccessToken(caller)));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private User persistUser(String first, String last, Role role) {
        User saved = userRepository.save(User.builder().firstName(first).lastName(last)
                .email("review-" + UUID.randomUUID() + "@test.local").passwordHash("irrelevant").role(role).build());
        userIds.add(saved.getId());
        return saved;
    }

    private void member(User user, OrganizationRole role, Set<OrganizationPermission> permissions) {
        OrganizationMember member = OrganizationMember.builder().userId(user.getId()).organizationId(orgId).build();
        member.getRoles().add(role);
        member.getPermissions().addAll(permissions);
        organizationMemberRepository.save(member);
        memberIds.add(user.getId());
    }

    private UUID event(Instant startAt, EventStatus status) {
        UUID id = eventRepository.save(Event.builder().organizationId(orgId).title("Review Test Event").description("desc")
                .category("music").status(status).ticketPrefix("R" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        eventIds.add(id);
        return id;
    }

    private void ticket(UUID eventId, User owner, TicketStatus status) {
        Ticket saved = ticketRepository.save(Ticket.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).eventId(eventId)
                .ticketTypeId(UUID.randomUUID()).ownerId(owner.getId())
                .ticketNumber("REV-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("test-credential-" + UUID.randomUUID()).credentialVersion(0).status(status).build());
        ticketIds.add(saved.getId());
    }
}
