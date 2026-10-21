package com.junaldadlawan.event_ticketing_api.notification.service;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.review.entity.EventReview;
import com.junaldadlawan.event_ticketing_api.review.repository.EventReviewRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-NOTIFY-002, driven with an explicit clock and limited to this test's own events. */
@SpringBootTest
@AutoConfigureMockMvc
class EventLifecycleNotificationIntegrationTest {

    @Autowired private EventLifecycleNotifier notifier;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private EventReviewRepository eventReviewRepository;

    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private UUID orgId;
    private final UUID ownerId = UUID.randomUUID();
    private String title;
    // start 7 days out at 10:18 UTC = 6:18 PM in Manila
    private final Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofMinutes(10 * 60 + 18));
    private final Instant end = start.plus(3, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        title = "Lifecycle " + UUID.randomUUID().toString().substring(0, 6);
        orgId = organizationRepository.save(Organization.builder().name("Lifecycle Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(ownerId).documents(List.of()).build()).getId();
    }

    @AfterEach
    void tearDown() {
        eventIds.forEach(id -> {
            jdbc.update("delete from notifications where event_id = ?", id);
            jdbc.update("delete from tickets where event_id = ?", id);
            jdbc.update("delete from ticket_types where event_id = ?", id);
            jdbc.update("delete from event_reviews where event_id = ?", id);
            jdbc.update("delete from audit_log_entries where event_id = ?", id);
        });
        eventIds.forEach(eventRepository::deleteById);
        userIds.forEach(id -> jdbc.update("delete from notification_preferences where user_id = ?", id));
        organizationMemberRepository.findByUserIdAndOrganizationId(ownerId, orgId).ifPresent(organizationMemberRepository::delete);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void eachScheduledNotificationFiresOnceAtItsTime_toCurrentHoldersOnly() throws Exception {
        UUID event = event(EventStatus.PUBLISHED, null, 60);
        UUID general = type(event);
        UUID vip = type(event);
        UUID both = user(), early = user(), late = user(), none = user(), attended = user(), reviewed = user(), refunded = user(), optedOut = user();
        ticket(event, general, both, TicketStatus.VALID);
        ticket(event, vip, both, TicketStatus.VALID);              // two tickets: still one notification of each kind
        ticket(event, general, early, TicketStatus.VALID);
        ticket(event, vip, late, TicketStatus.VALID);
        ticket(event, general, none, TicketStatus.VALID);
        ticket(event, general, attended, TicketStatus.USED);
        ticket(event, general, reviewed, TicketStatus.USED);
        ticket(event, general, refunded, TicketStatus.REFUNDED);   // not a holder any more
        ticket(event, vip, optedOut, TicketStatus.VALID);
        eventReviewRepository.save(EventReview.builder().eventId(event).userId(reviewed).rating(5).build());
        preferences(optedOut, "{\"EVENT_REMINDER\":false}").andExpect(status().isOk());
        Set<UUID> holders = Set.of(both, early, late, none, attended, reviewed, optedOut);

        run(start.minus(24, ChronoUnit.HOURS).minusSeconds(60), event);
        assertThat(sent(event, NotificationType.EVENT_REMINDER)).isEmpty();

        run(start.minus(24, ChronoUnit.HOURS), event);
        assertThat(sent(event, NotificationType.EVENT_REMINDER)).containsOnlyKeys(withoutOptedOut(holders, optedOut))
                .containsValue(title + " is tomorrow at 6:18 PM.");
        run(start.minus(23, ChronoUnit.HOURS), event);   // a second run: nothing new
        assertThat(count(event, NotificationType.EVENT_REMINDER)).isEqualTo(6);

        // the event's gates open 60 minutes before it starts
        run(start.minus(61, ChronoUnit.MINUTES), event);
        assertThat(sent(event, NotificationType.GATE_OPEN)).isEmpty();
        run(start.minus(60, ChronoUnit.MINUTES), event);
        assertThat(sent(event, NotificationType.GATE_OPEN)).containsOnlyKeys(holders)
                .containsValue("Gates are open for " + title + ".");
        run(start.minus(30, ChronoUnit.MINUTES), event);

        run(start, event);
        assertThat(sent(event, NotificationType.EVENT_STARTING)).containsOnlyKeys(holders)
                .containsValue(title + " is starting now.");

        run(end, event);
        run(end.plus(5, ChronoUnit.MINUTES), event);
        assertThat(sent(event, NotificationType.REVIEW_REQUEST)).containsOnlyKeys(attended)
                .containsValue("How was " + title + "? Rate it.");

        assertThat(count(event, NotificationType.EVENT_STARTING)).isEqualTo(7);
        assertThat(count(event, NotificationType.GATE_OPEN)).isEqualTo(7);

        // an event without a gate time sends no gates-open notification
        UUID noGate = event(EventStatus.PUBLISHED, null, null);
        ticket(noGate, type(noGate), early, TicketStatus.VALID);
        run(start, noGate);
        assertThat(sent(noGate, NotificationType.GATE_OPEN)).isEmpty();
        assertThat(sent(noGate, NotificationType.EVENT_STARTING)).containsOnlyKeys(early);
    }

    @Test
    void nothingForDraftOrCancelledEvents_norForTimesBeforeItWasPublished() {
        UUID holder = user();
        UUID draft = event(EventStatus.DRAFT, null, 30);
        UUID cancelled = event(EventStatus.CANCELLED, null, 30);
        // published 2 hours before it starts: the reminder's time had already passed
        UUID lastMinute = event(EventStatus.PUBLISHED, start.minus(2, ChronoUnit.HOURS), 30);
        for (UUID e : List.of(draft, cancelled, lastMinute)) {
            ticket(e, type(e), holder, TicketStatus.VALID);
        }
        Set<UUID> all = Set.of(draft, cancelled, lastMinute);

        for (Instant now : List.of(start.minus(24, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS), start.minus(30, ChronoUnit.MINUTES), start, end)) {
            notifier.run(now, all);
        }
        assertThat(jdbc.queryForObject("select count(*) from notifications where event_id in (?, ?)", Long.class, draft, cancelled)).isZero();
        assertThat(sent(lastMinute, NotificationType.EVENT_REMINDER)).isEmpty();
        assertThat(sent(lastMinute, NotificationType.GATE_OPEN)).containsOnlyKeys(holder);
        assertThat(sent(lastMinute, NotificationType.EVENT_STARTING)).containsOnlyKeys(holder);
    }

    @Test
    void eventChange_onlyForTimeOrVenueChanges_withBeforeAndAfter_inTheEventsTimezone() throws Exception {
        UUID event = event(EventStatus.PUBLISHED, null, null);
        UUID holder = user(), optedOut = user();
        UUID type = type(event);
        ticket(event, type, holder, TicketStatus.VALID);
        ticket(event, type, optedOut, TicketStatus.VALID);
        preferences(optedOut, "{\"EVENT_CHANGE\":false}").andExpect(status().isOk());

        // a title change through the API is not a move
        Event saved = eventRepository.findById(event).orElseThrow();
        mockMvc.perform(patch("/api/v1/events/{id}", event).header("Authorization", bearerOwner(saved))
                .contentType("application/json").content("{\"title\":\"" + title + "\"}")).andExpect(status().isOk());
        assertThat(sent(event, NotificationType.EVENT_CHANGE)).isEmpty();

        Instant oldStart = Instant.parse("2027-10-17T10:18:00Z");
        Event moved = eventRepository.findById(event).orElseThrow();
        moved.setStartAt(Instant.parse("2027-10-18T11:00:00Z"));
        moved.setEndAt(Instant.parse("2027-10-18T14:00:00Z"));
        notifier.eventChanged(moved, oldStart, Instant.parse("2027-10-18T14:00:00Z"), "Asia/Manila", null);

        assertThat(sent(event, NotificationType.EVENT_CHANGE)).containsOnlyKeys(holder)
                .containsValue(title + " moved: starts Oct 18, 2027, 7:00 PM (was Oct 17, 6:18 PM).");
        assertThat(eventRepository.findById(event).orElseThrow().getNotificationsFrom()).isNotNull();

        // the same values again: no change, nothing sent
        notifier.eventChanged(moved, moved.getStartAt(), moved.getEndAt(), "Asia/Manila", null);
        assertThat(count(event, NotificationType.EVENT_CHANGE)).isEqualTo(1);
    }

    @Test
    void preferencesDefaultToOn_takePartialMaps_andRefuseUnknownOrMandatoryTypes() throws Exception {
        UUID me = user();
        as(get("/api/v1/users/me/notification-preferences"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.length()").value(6))
                .andExpect(jsonPath("$.preferences.EVENT_REMINDER").value(true))
                .andExpect(jsonPath("$.preferences.WAITLIST_AVAILABILITY").value(true));
        preferences(me, "{\"GATE_OPEN\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.GATE_OPEN").value(false))
                .andExpect(jsonPath("$.preferences.EVENT_STARTING").value(true));
        preferences(me, "{\"ORDER_CONFIRMATION\":false}").andExpect(status().isBadRequest());
        preferences(me, "{\"NOPE\":true}").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/users/me/notification-preferences")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private void run(Instant now, UUID event) {
        notifier.run(now, Set.of(event));
    }

    /** userId -> message of this type for the event. */
    private Map<UUID, String> sent(UUID event, NotificationType type) {
        Map<UUID, String> sent = new java.util.HashMap<>();
        jdbc.query("select user_id, message from notifications where event_id = ? and type = ?",
                rs -> { sent.put(rs.getObject(1, UUID.class), rs.getString(2)); }, event, type.name());
        return sent;
    }

    private long count(UUID event, NotificationType type) {
        return jdbc.queryForObject("select count(*) from notifications where event_id = ? and type = ?", Long.class, event, type.name());
    }

    private static UUID[] withoutOptedOut(Set<UUID> holders, UUID optedOut) {
        return holders.stream().filter(id -> !id.equals(optedOut)).toArray(UUID[]::new);
    }

    private ResultActions preferences(UUID userId, String body) throws Exception {
        return as(put("/api/v1/users/me/notification-preferences").contentType("application/json").content("{\"preferences\":" + body + "}"), userId);
    }

    private ResultActions as(MockHttpServletRequestBuilder request, UUID userId) throws Exception {
        User user = User.builder().id(userId).email("life-" + userId + "@test.local").role(Role.CUSTOMER).build();
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.generateAccessToken(user)));
    }

    /** The organization's owner (only owners and organizers may edit an event). */
    private String bearerOwner(Event event) {
        OrganizationMember member = OrganizationMember.builder().userId(ownerId).organizationId(event.getOrganizationId()).build();
        member.getRoles().add(OrganizationRole.OWNER);
        organizationMemberRepository.save(member);
        User owner = User.builder().id(ownerId).email("life-owner-" + ownerId + "@test.local").role(Role.CUSTOMER).build();
        return "Bearer " + jwtService.generateAccessToken(owner);
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        userIds.add(id);
        return id;
    }

    private UUID event(EventStatus status, Instant notificationsFrom, Integer gateMinutes) {
        UUID id = eventRepository.save(Event.builder().organizationId(orgId).title(title).description("desc").category("music")
                .status(status).ticketPrefix("N" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(start).endAt(end).timezone("Asia/Manila").notificationsFrom(notificationsFrom).gateOpensMinutesBefore(gateMinutes).build()).getId();
        eventIds.add(id);
        return id;
    }

    private UUID type(UUID eventId) {
        return ticketTypeRepository.save(TicketType.builder().eventId(eventId).name("T" + UUID.randomUUID().toString().substring(0, 4))
                .kind(TicketTypeKind.GENERAL_ADMISSION).price(Money.builder().amount(1000L).currency("PHP").build())
                .quantityTotal(10).quantityAvailable(10).saleStartAt(Instant.now()).saleEndAt(start.minus(2, ChronoUnit.HOURS))
                .maxPerOrder(10).position(0).build()).getId();
    }

    private void ticket(UUID eventId, UUID typeId, UUID ownerId, TicketStatus status) {
        ticketRepository.save(Ticket.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(typeId)
                .ownerId(ownerId).ticketNumber("NTF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("test-credential-" + UUID.randomUUID()).credentialVersion(0).status(status).build());
    }
}
