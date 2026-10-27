package com.junaldadlawan.event_ticketing_api.analytics;

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
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The per-day share history on the real database: each metric lands in its own column of today's row, in the event's own time zone. */
@SpringBootTest
@AutoConfigureMockMvc
class ShareTimelineIntegrationTest {

    private static final List<String> METRICS = List.of("shareButton", "copyLink", "qrGenerate", "linkOpen", "qrScan", "linkBuy", "qrBuy");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
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

    private UUID org;
    private String owner;
    private String analyticsOrganizer;
    private String eventsOnlyOrganizer;
    private String admin;
    private String stranger;
    private String customer;

    @BeforeEach
    void setUp() {
        User ownerUser = user(Role.CUSTOMER);
        User analyticsUser = user(Role.CUSTOMER);
        User eventsOnlyUser = user(Role.CUSTOMER);
        owner = token(ownerUser);
        analyticsOrganizer = token(analyticsUser);
        eventsOnlyOrganizer = token(eventsOnlyUser);
        admin = token(user(Role.ADMIN));
        stranger = token(user(Role.CUSTOMER));
        customer = token(user(Role.CUSTOMER));
        org = organizationRepository.save(Organization.builder().name("Share Timeline Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        orgIds.add(org);
        member(ownerUser, OrganizationRole.OWNER, null);
        member(analyticsUser, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.ANALYTICS));
        member(eventsOnlyUser, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.EVENTS));
    }

    @AfterEach
    void tearDown() {
        eventIds.forEach(id -> {
            jdbc.update("delete from audit_log_entries where event_id = ?", id);
            jdbc.update("delete from share_daily_counts where event_id = ?", id);
            jdbc.update("delete from share_hourly_counts where event_id = ?", id);
            eventRepository.deleteById(id);
        });
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(organizationRepository::deleteById);
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("share-timeline-" + UUID.randomUUID() + "@test.local").role(role).build();
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

    private UUID event(String timezone) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID id = eventRepository.save(Event.builder().organizationId(org).title("Timeline " + UUID.randomUUID()).description("d").category("music")
                .status(EventStatus.ON_SALE).ticketPrefix(freePrefix()).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone(timezone).build()).getId();
        eventIds.add(id);
        return id;
    }

    private String freePrefix() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        java.util.Random random = new java.util.Random();
        while (true) {
            String candidate = "" + letters.charAt(random.nextInt(26)) + letters.charAt(random.nextInt(26)) + letters.charAt(random.nextInt(26));
            if (jdbc.queryForObject("select count(*) from events where ticket_prefix = ?", Integer.class, candidate) == 0) {
                return candidate;
            }
        }
    }

    private void click(UUID id, String action) throws Exception {
        mockMvc.perform(post("/api/v1/events/{id}/share-clicks", id).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"))
                .andExpect(status().isNoContent());
    }

    private void buy(UUID id, String source) throws Exception {
        mockMvc.perform(post("/api/v1/events/{id}/share-buy-clicks", id).contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"" + source + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String codeOf(UUID id) throws Exception {
        String body = mockMvc.perform(get("/api/v1/events/{id}/share-link", id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("code").asString();
    }

    private void open(String code, String source) throws Exception {
        mockMvc.perform(post("/api/v1/share-links/{code}/open", code).contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"" + source + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode timeline(UUID id, String token, String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline" + query, id).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** The day's counts of the seven metrics, in {@link #METRICS} order. */
    private List<Long> counts(JsonNode day) {
        return METRICS.stream().map(metric -> day.get(metric).asLong()).toList();
    }

    private JsonNode day(JsonNode timeline, LocalDate day) {
        for (JsonNode row : timeline.get("days")) {
            if (row.get("day").asString().equals(day.toString())) {
                return row;
            }
        }
        throw new AssertionError("no row for " + day);
    }

    private Map<String, Object> dailyRow(UUID id, LocalDate day) {
        return jdbc.queryForMap("select * from share_daily_counts where event_id = ? and day = ?", id, day);
    }

    @Test
    void eachMetric_landsInItsOwnColumnOfTodaysRow_andRepeatedCallsAddUp() throws Exception {
        UUID id = event("UTC");
        String code = codeOf(id);
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));

        click(id, "SHARE_BUTTON");
        assertThat(counts(day(timeline(id, owner, ""), today))).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L);
        click(id, "COPY_LINK");
        click(id, "QR_GENERATE");
        open(code, "LINK");
        open(code, "QR");
        buy(id, "LINK");
        buy(id, "QR");
        assertThat(counts(day(timeline(id, owner, ""), today))).containsExactly(1L, 1L, 1L, 1L, 1L, 1L, 1L);

        for (int i = 0; i < 2; i++) {
            click(id, "COPY_LINK");
            open(code, "QR");
        }
        assertThat(counts(day(timeline(id, owner, ""), today))).containsExactly(1L, 3L, 1L, 1L, 3L, 1L, 1L);
        assertThat(jdbc.queryForObject("select count(*) from share_daily_counts where event_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void anUnknownActionOrSource_countsLikeTheTotalsDo() throws Exception {
        UUID id = event("UTC");
        String code = codeOf(id);
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));

        click(id, "SOMETHING_ELSE");   // counts as SHARE_BUTTON
        open(code, "CARRIER_PIGEON");  // counts as LINK
        buy(id, "CARRIER_PIGEON");     // counts nothing

        assertThat(counts(day(timeline(id, owner, ""), today))).containsExactly(1L, 0L, 0L, 1L, 0L, 0L, 0L);
    }

    @Test
    void theDay_isTheEventsOwnCalendarDay() throws Exception {
        UUID ahead = event("Pacific/Kiritimati");     // UTC+14
        UUID behind = event("Pacific/Pago_Pago");     // UTC-11: always on a different calendar day than Kiritimati
        LocalDate aheadToday = LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
        LocalDate behindToday = LocalDate.now(ZoneId.of("Pacific/Pago_Pago"));

        click(ahead, "COPY_LINK");
        click(behind, "COPY_LINK");

        assertThat(aheadToday).isNotEqualTo(behindToday);
        assertThat(dailyRow(ahead, aheadToday).get("copy_link")).isEqualTo(1L);
        assertThat(dailyRow(behind, behindToday).get("copy_link")).isEqualTo(1L);
        JsonNode aheadTimeline = timeline(ahead, owner, "");
        assertThat(aheadTimeline.get("timezone").asString()).isEqualTo("Pacific/Kiritimati");
        assertThat(counts(day(aheadTimeline, aheadToday))).containsExactly(0L, 1L, 0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void everyDayOfTheRangeHasARow_zerosIncluded_andTheRangeFilters() throws Exception {
        UUID id = event("UTC");
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        jdbc.update("insert into share_daily_counts (event_id, day, share_button, link_buy) values (?, ?, 4, 2)", id, today.minusDays(5));
        jdbc.update("insert into share_daily_counts (event_id, day, copy_link) values (?, ?, 7)", id, today.minusDays(2));
        click(id, "QR_GENERATE");

        JsonNode all = timeline(id, owner, "");
        assertThat(all.get("trackingSince").asString()).isEqualTo(today.minusDays(5).toString());
        assertThat(all.get("days")).hasSize(6);   // from the first row to today
        assertThat(counts(day(all, today.minusDays(5)))).containsExactly(4L, 0L, 0L, 0L, 0L, 2L, 0L);
        assertThat(counts(day(all, today.minusDays(4)))).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        assertThat(counts(day(all, today.minusDays(2)))).containsExactly(0L, 7L, 0L, 0L, 0L, 0L, 0L);
        assertThat(counts(day(all, today))).containsExactly(0L, 0L, 1L, 0L, 0L, 0L, 0L);

        JsonNode window = timeline(id, owner, "?from=" + today.minusDays(3) + "&to=" + today.minusDays(1));
        assertThat(window.get("days")).hasSize(3);
        assertThat(window.get("days").get(0).get("day").asString()).isEqualTo(today.minusDays(3).toString());
        assertThat(counts(window.get("days").get(1))).containsExactly(0L, 7L, 0L, 0L, 0L, 0L, 0L);
        assertThat(window.get("trackingSince").asString()).isEqualTo(today.minusDays(5).toString());   // not narrowed by the range

        JsonNode beforeAnyCount = timeline(id, owner, "?from=" + today.minusDays(9) + "&to=" + today.minusDays(7));
        assertThat(beforeAnyCount.get("days")).hasSize(3);
        beforeAnyCount.get("days").forEach(row -> assertThat(counts(row)).containsOnly(0L));
    }

    @Test
    void anEventNothingWasCountedFor_hasNoTrackingSince_andOneZeroDayRow() throws Exception {
        UUID id = event("UTC");

        JsonNode timeline = timeline(id, owner, "");

        assertThat(timeline.get("trackingSince").isNull()).isTrue();
        assertThat(timeline.get("days")).hasSize(1);
        assertThat(counts(timeline.get("days").get(0))).containsOnly(0L);
    }

    @Test
    void aBadRangeIsRefused() throws Exception {
        UUID id = event("UTC");

        mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline?from=2026-02-01&to=2026-01-01", id).header("Authorization", owner))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline?from=2020-01-01&to=2026-01-01", id).header("Authorization", owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTotalsOnTheEventStayInStep_withTheDailyRows() throws Exception {
        UUID id = event("UTC");
        String code = codeOf(id);
        click(id, "SHARE_BUTTON");
        click(id, "COPY_LINK");
        click(id, "COPY_LINK");
        click(id, "QR_GENERATE");
        open(code, "LINK");
        open(code, "QR");
        open(code, "QR");
        buy(id, "LINK");
        buy(id, "QR");

        Map<String, Object> totals = jdbc.queryForMap("select share_click_count, share_copy_count, share_qr_count, share_link_open_count,"
                + " share_qr_scan_count, share_link_buy_count, share_qr_buy_count from events where id = ?", id);
        Map<String, Object> daily = jdbc.queryForMap("select sum(share_button) a, sum(copy_link) b, sum(qr_generate) c, sum(link_open) d, sum(qr_scan) e,"
                + " sum(link_buy) f, sum(qr_buy) g from share_daily_counts where event_id = ?", id);
        assertThat(List.of(totals.get("share_click_count"), totals.get("share_copy_count"), totals.get("share_qr_count"), totals.get("share_link_open_count"),
                totals.get("share_qr_scan_count"), totals.get("share_link_buy_count"), totals.get("share_qr_buy_count")))
                .containsExactly(1L, 2L, 1L, 1L, 2L, 1L, 1L);
        assertThat(List.of("a", "b", "c", "d", "e", "f", "g").stream().map(key -> ((Number) daily.get(key)).longValue()).toList())
                .containsExactly(1L, 2L, 1L, 1L, 2L, 1L, 1L);
    }

    @Test
    void anEventThatIsNotPublic_countsNoDailyRow() throws Exception {
        UUID id = event("UTC");
        jdbc.update("update events set status = 'DRAFT' where id = ?", id);

        click(id, "COPY_LINK");
        buy(id, "LINK");

        assertThat(jdbc.queryForObject("select count(*) from share_daily_counts where event_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void authorization_ownerAnalyticsOrganizerAndAdminAreAllowed_othersAreRefused() throws Exception {
        UUID id = event("UTC");

        for (String allowed : List.of(owner, analyticsOrganizer, admin)) {
            mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline", id).header("Authorization", allowed)).andExpect(status().isOk());
        }
        for (String refused : List.of(eventsOnlyOrganizer, stranger, customer)) {
            mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline", id).header("Authorization", refused)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline", UUID.randomUUID()).header("Authorization", owner)).andExpect(status().isNotFound());
    }

    // ---- by hour ----

    /** The summed counts over all 24 hours, in {@link #METRICS} order. */
    private List<Long> hourTotals(JsonNode timeline) {
        return METRICS.stream().map(metric -> {
            long sum = 0;
            for (JsonNode hour : timeline.get("byHour")) {
                sum += hour.get(metric).asLong();
            }
            return sum;
        }).toList();
    }

    /** The hours that have any count. */
    private List<Integer> busyHours(JsonNode timeline) {
        List<Integer> hours = new ArrayList<>();
        for (JsonNode hour : timeline.get("byHour")) {
            if (counts(hour).stream().anyMatch(count -> count > 0)) {
                hours.add(hour.get("hour").asInt());
            }
        }
        return hours;
    }

    private void everyMetricOnce(UUID id) throws Exception {
        String code = codeOf(id);
        click(id, "SHARE_BUTTON");
        click(id, "COPY_LINK");
        click(id, "QR_GENERATE");
        open(code, "LINK");
        open(code, "QR");
        buy(id, "LINK");
        buy(id, "QR");
    }

    @Test
    void eachMetric_landsInTheRightHourOfTheEventsTimezone() throws Exception {
        UUID id = event("Asia/Kolkata");
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        int hourBefore = java.time.ZonedDateTime.now(zone).getHour();

        everyMetricOnce(id);

        int hourAfter = java.time.ZonedDateTime.now(zone).getHour();   // the clock may tick over an hour while the calls run
        JsonNode timeline = timeline(id, owner, "");
        assertThat(hourTotals(timeline)).containsExactly(1L, 1L, 1L, 1L, 1L, 1L, 1L);
        assertThat(busyHours(timeline)).isNotEmpty().allMatch(hour -> hour == hourBefore || hour == hourAfter);
        // each metric is in its own column of an hour row
        for (int i = 0; i < METRICS.size(); i++) {
            String metric = METRICS.get(i);
            long inColumn = 0;
            for (JsonNode hour : timeline.get("byHour")) {
                if (hour.get(metric).asLong() > 0) {
                    inColumn += hour.get(metric).asLong();
                }
            }
            assertThat(inColumn).as(metric).isEqualTo(1L);
        }
    }

    @Test
    void theHour_followsTheEventsTimezone() throws Exception {
        UUID utc = event("UTC");
        UUID ahead = event("Pacific/Kiritimati");   // UTC+14: never the same hour of the day as UTC
        int utcBefore = java.time.ZonedDateTime.now(ZoneId.of("UTC")).getHour();
        int aheadBefore = java.time.ZonedDateTime.now(ZoneId.of("Pacific/Kiritimati")).getHour();

        click(utc, "COPY_LINK");
        click(ahead, "COPY_LINK");

        int utcAfter = java.time.ZonedDateTime.now(ZoneId.of("UTC")).getHour();
        int aheadAfter = java.time.ZonedDateTime.now(ZoneId.of("Pacific/Kiritimati")).getHour();
        assertThat(busyHours(timeline(utc, owner, ""))).hasSize(1).allMatch(hour -> hour == utcBefore || hour == utcAfter);
        assertThat(busyHours(timeline(ahead, owner, ""))).hasSize(1).allMatch(hour -> hour == aheadBefore || hour == aheadAfter);
        assertThat((aheadBefore - utcBefore + 24) % 24).isEqualTo(14);
    }

    @Test
    void byHour_alwaysHas24Entries_zerosIncluded() throws Exception {
        UUID id = event("UTC");
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));

        for (String query : List.of("", "?from=" + today.minusDays(9) + "&to=" + today.minusDays(7), "?from=" + today + "&to=" + today)) {
            JsonNode byHour = timeline(id, owner, query).get("byHour");
            assertThat(byHour).hasSize(24);
            for (int hour = 0; hour < 24; hour++) {
                assertThat(byHour.get(hour).get("hour").asInt()).isEqualTo(hour);
                assertThat(counts(byHour.get(hour))).containsOnly(0L);
            }
        }
    }

    @Test
    void byHour_sumsTheRequestedRange_andLeavesOutOtherDays() throws Exception {
        UUID id = event("UTC");
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        jdbc.update("insert into share_hourly_counts (event_id, day, hour, link_buy) values (?, ?, 3, 2)", id, today.minusDays(5));
        jdbc.update("insert into share_hourly_counts (event_id, day, hour, copy_link) values (?, ?, 3, 7)", id, today.minusDays(2));
        jdbc.update("insert into share_hourly_counts (event_id, day, hour, share_button) values (?, ?, 15, 1)", id, today.minusDays(2));
        jdbc.update("insert into share_hourly_counts (event_id, day, hour, copy_link) values (?, ?, 3, 1)", id, today.minusDays(1));

        JsonNode full = timeline(id, owner, "?from=" + today.minusDays(6) + "&to=" + today);
        assertThat(counts(full.get("byHour").get(3))).containsExactly(0L, 8L, 0L, 0L, 0L, 2L, 0L);   // copy 7 + 1, buy 2 summed over days
        assertThat(counts(full.get("byHour").get(15))).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L);

        JsonNode window = timeline(id, owner, "?from=" + today.minusDays(3) + "&to=" + today.minusDays(2));
        assertThat(counts(window.get("byHour").get(3))).containsExactly(0L, 7L, 0L, 0L, 0L, 0L, 0L);
        assertThat(counts(window.get("byHour").get(15))).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L);

        JsonNode elsewhere = timeline(id, owner, "?from=" + today.minusDays(9) + "&to=" + today.minusDays(7));
        assertThat(hourTotals(elsewhere)).containsOnly(0L);
    }

    @Test
    void countsFromBeforeTheHourlyHistory_areLeftOutOfByHour_notSpread() throws Exception {
        UUID id = event("UTC");
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        jdbc.update("insert into share_daily_counts (event_id, day, share_button, link_buy) values (?, ?, 4, 2)", id, today.minusDays(3));

        JsonNode timeline = timeline(id, owner, "");

        assertThat(counts(day(timeline, today.minusDays(3)))).containsExactly(4L, 0L, 0L, 0L, 0L, 2L, 0L);   // the day keeps them
        assertThat(timeline.get("byHour")).hasSize(24);
        assertThat(hourTotals(timeline)).containsOnly(0L);                                                  // the hours do not
    }

    @Test
    void theHourlyRows_addUpToTheDailyRowsAndTheTotals() throws Exception {
        UUID id = event("UTC");
        everyMetricOnce(id);
        click(id, "COPY_LINK");

        Map<String, Object> hourly = jdbc.queryForMap("select sum(share_button) a, sum(copy_link) b, sum(qr_generate) c, sum(link_open) d, sum(qr_scan) e,"
                + " sum(link_buy) f, sum(qr_buy) g from share_hourly_counts where event_id = ?", id);
        Map<String, Object> daily = jdbc.queryForMap("select sum(share_button) a, sum(copy_link) b, sum(qr_generate) c, sum(link_open) d, sum(qr_scan) e,"
                + " sum(link_buy) f, sum(qr_buy) g from share_daily_counts where event_id = ?", id);
        List<Long> expected = List.of(1L, 2L, 1L, 1L, 1L, 1L, 1L);
        assertThat(List.of("a", "b", "c", "d", "e", "f", "g").stream().map(key -> ((Number) hourly.get(key)).longValue()).toList()).isEqualTo(expected);
        assertThat(List.of("a", "b", "c", "d", "e", "f", "g").stream().map(key -> ((Number) daily.get(key)).longValue()).toList()).isEqualTo(expected);
    }

    @Test
    void byHour_isRefusedToTheSamePeopleAsTheTimeline() throws Exception {
        UUID id = event("UTC");

        for (String allowed : List.of(owner, analyticsOrganizer, admin)) {
            assertThat(timeline(id, allowed, "").get("byHour")).hasSize(24);
        }
        for (String refused : List.of(eventsOnlyOrganizer, stranger, customer)) {
            mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline", id).header("Authorization", refused)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/events/{id}/analytics/share-timeline", id)).andExpect(status().isUnauthorized());
    }
}
