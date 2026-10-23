package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.DraftEventCleanupService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unpublished drafts are deleted when their start is less than the cutoff away (or past), the team is told, and warned a day ahead. The job is
 * called directly (the scheduler is off in tests) and limited to the test's own events, so the developer's data is untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DraftEventCleanupIntegrationTest {

    @Autowired
    private DraftEventCleanupService cleanup;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private int originalCutoff;

    private User ownerUser;
    private User eventsOrganizer;
    private User noEventsOrganizer;
    private String admin;
    private String customer;
    private UUID org;

    @BeforeEach
    void setUp() {
        originalCutoff = jdbc.queryForObject("select auto_delete_minutes_before_start from draft_event_settings where id = 1", Integer.class);
        jdbc.update("update draft_event_settings set auto_delete_minutes_before_start = 60 where id = 1");
        ownerUser = user(Role.CUSTOMER);
        eventsOrganizer = user(Role.CUSTOMER);
        noEventsOrganizer = user(Role.CUSTOMER);
        admin = token(user(Role.ADMIN));
        customer = token(user(Role.CUSTOMER));
        org = organizationRepository.save(Organization.builder().name("Draft Cleanup Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        orgIds.add(org);
        member(ownerUser, OrganizationRole.OWNER, null);
        member(eventsOrganizer, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.EVENTS));
        member(noEventsOrganizer, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.ANALYTICS));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update draft_event_settings set auto_delete_minutes_before_start = ? where id = 1", originalCutoff);
        eventIds.forEach(id -> {
            jdbc.update("delete from notifications where event_id = ?", id);
            jdbc.update("delete from audit_log_entries where event_id = ?", id);
        });
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        eventIds.forEach(eventRepository::deleteById);
        orgIds.forEach(organizationRepository::deleteById);
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("draft-it-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private void member(User user, OrganizationRole role, Set<OrganizationPermission> permissions) {
        OrganizationMember member = OrganizationMember.builder().userId(user.getId()).organizationId(org).build();
        member.getRoles().add(role);
        if (permissions != null) {
            member.choosePermissions(permissions);
        }
        members.add(organizationMemberRepository.save(member));
    }

    private UUID event(EventStatus status, Duration startsIn, String title) {
        Instant start = Instant.now().plus(startsIn);
        UUID id = eventRepository.save(Event.builder().organizationId(org).title(title).description("d").category("music").status(status)
                .ticketPrefix("D" + UUID.randomUUID().toString().substring(0, 2).toUpperCase()).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS))
                .timezone("UTC").build()).getId();
        eventIds.add(id);
        return id;
    }

    private int notifications(UUID eventId, String type) {
        return jdbc.queryForObject("select count(*) from notifications where event_id = ? and type = ?", Integer.class, eventId, type);
    }

    private boolean deleted(UUID eventId) {
        return jdbc.queryForObject("select deleted_at is not null from events where id = ?", Boolean.class, eventId);
    }

    @Test
    void draftsStartingWithinTheCutoffOrPast_areSoftDeleted_auditedAndTheTeamIsTold_once() throws Exception {
        UUID soon = event(EventStatus.DRAFT, Duration.ofMinutes(30), "Soon Draft");
        UUID past = event(EventStatus.DRAFT, Duration.ofHours(-3), "Past Draft");
        UUID tomorrow = event(EventStatus.DRAFT, Duration.ofHours(10), "Tomorrow Draft");   // inside the 24h warning window
        UUID later = event(EventStatus.DRAFT, Duration.ofDays(5), "Later Draft");
        UUID published = event(EventStatus.PUBLISHED, Duration.ofMinutes(10), "Published Soon");
        Set<UUID> mine = new HashSet<>(eventIds);

        DraftEventCleanupService.Result result = cleanup.run(Instant.now(), mine);

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.warned()).isEqualTo(2);   // the owner and the organizer with EVENTS, for "Tomorrow Draft"
        assertThat(deleted(soon)).isTrue();
        assertThat(deleted(past)).isTrue();
        assertThat(deleted(tomorrow)).isFalse();
        assertThat(deleted(later)).isFalse();
        assertThat(deleted(published)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from events where id = ?", Integer.class, soon)).isEqualTo(1);   // soft delete: the row stays

        // the owner and the organizer with EVENTS are told; the organizer without EVENTS is not
        assertThat(notifications(soon, "EVENT_DRAFT_DELETED")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from notifications where event_id = ? and type = 'EVENT_DRAFT_DELETED' and user_id = ?",
                Integer.class, soon, noEventsOrganizer.getId())).isZero();
        assertThat(jdbc.queryForObject("select message from notifications where event_id = ? and type = 'EVENT_DRAFT_DELETED' limit 1", String.class, soon))
                .isEqualTo("Soon Draft was deleted because it was not published at least 1 hour before it started.");
        assertThat(notifications(tomorrow, "EVENT_DRAFT_WARNING")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select message from notifications where event_id = ? and type = 'EVENT_DRAFT_WARNING' limit 1", String.class, tomorrow))
                .startsWith("Tomorrow Draft is still a draft and will be deleted at ").endsWith(" unless you publish it.");
        assertThat(notifications(later, "EVENT_DRAFT_WARNING")).isZero();

        // audit: by the system, with the title, the start and the reason
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where event_id = ? and action = 'event.auto_deleted_unpublished' and actor_id is null",
                Integer.class, soon)).isEqualTo(1);
        String details = jdbc.queryForObject("select details::text from audit_log_entries where event_id = ? and action = 'event.auto_deleted_unpublished'", String.class, soon);
        assertThat(details).contains("Soon Draft").contains("startAt").contains("Not published at least 1 hour before it started");

        // running again changes nothing: nobody is told twice, nothing is deleted twice
        assertThat(cleanup.run(Instant.now(), mine)).isEqualTo(new DraftEventCleanupService.Result(0, 0));
        assertThat(notifications(soon, "EVENT_DRAFT_DELETED")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where event_id = ? and action = 'event.auto_deleted_unpublished'", Integer.class, soon)).isEqualTo(1);

        // the deleted draft is gone for the API; the survivor shows when it will be deleted
        mockMvc.perform(get("/api/v1/events/{id}", soon).header("Authorization", token(ownerUser))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/{id}", tomorrow).header("Authorization", token(ownerUser))).andExpect(status().isOk())
                .andExpect(jsonPath("$.autoDeleteAt").exists());
        mockMvc.perform(get("/api/v1/events/{id}", published).header("Authorization", token(ownerUser))).andExpect(status().isOk())
                .andExpect(jsonPath("$.autoDeleteAt").doesNotExist());
    }

    @Test
    void theCutoffIsAnAdminSetting_andZeroSwitchesTheRuleOff() throws Exception {
        UUID inNinetyMinutes = event(EventStatus.DRAFT, Duration.ofMinutes(90), "Ninety Draft");
        Set<UUID> mine = new HashSet<>(eventIds);

        mockMvc.perform(put("/api/v1/platform-settings/draft-events").header("Authorization", customer).contentType("application/json")
                .content("{\"autoDeleteMinutesBeforeStart\":120}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/platform-settings/draft-events").header("Authorization", admin).contentType("application/json")
                .content("{\"autoDeleteMinutesBeforeStart\":10081}")).andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/platform-settings/draft-events").header("Authorization", admin).contentType("application/json")
                .content("{\"autoDeleteMinutesBeforeStart\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.autoDeleteMinutesBeforeStart").value(0));
        assertThat(cleanup.run(Instant.now(), mine)).isEqualTo(new DraftEventCleanupService.Result(0, 0));   // off
        assertThat(deleted(inNinetyMinutes)).isFalse();

        mockMvc.perform(put("/api/v1/platform-settings/draft-events").header("Authorization", admin).contentType("application/json")
                .content("{\"autoDeleteMinutesBeforeStart\":120}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/platform-settings/draft-events").header("Authorization", customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.autoDeleteMinutesBeforeStart").value(120));
        assertThat(cleanup.run(Instant.now(), mine).deleted()).isEqualTo(1);   // 90 minutes away is inside a 2 hour cutoff
        assertThat(deleted(inNinetyMinutes)).isTrue();
        assertThat(jdbc.queryForObject("select message from notifications where event_id = ? and type = 'EVENT_DRAFT_DELETED' limit 1", String.class, inNinetyMinutes))
                .contains("at least 2 hours before");
    }
}
