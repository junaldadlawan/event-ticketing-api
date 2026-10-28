package com.junaldadlawan.event_ticketing_api.analytics;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Share tracking on the real database: every counter changes only on its own action, source or endpoint; the short link; the analytics numbers. */
@SpringBootTest
@AutoConfigureMockMvc
class ShareTrackingIntegrationTest {

    /** The seven counters of an event, in the order of {@link #counts}. */
    private static final List<String> COLUMNS = List.of("share_click_count", "share_copy_count", "share_qr_count", "share_link_open_count",
            "share_qr_scan_count", "share_link_buy_count", "share_qr_buy_count");

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

    private UUID org;
    private String owner;
    private String stranger;
    private String customer;

    @BeforeEach
    void setUp() {
        User ownerUser = user(Role.CUSTOMER);
        owner = token(ownerUser);
        stranger = token(user(Role.CUSTOMER));
        customer = token(user(Role.CUSTOMER));
        org = organizationRepository.save(Organization.builder().name("Share Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        orgIds.add(org);
        OrganizationMember member = OrganizationMember.builder().userId(ownerUser.getId()).organizationId(org).build();
        member.getRoles().add(OrganizationRole.OWNER);
        members.add(organizationMemberRepository.save(member));
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
        return User.builder().id(UUID.randomUUID()).email("share-it-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private UUID event(EventStatus status) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID id = eventRepository.save(Event.builder().organizationId(org).title("Share " + UUID.randomUUID()).description("d").category("music")
                .status(status).ticketPrefix(freePrefix()).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
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

    /** The seven counters of the event, by column. */
    private Map<String, Long> counts(UUID id) {
        Map<String, Object> row = jdbc.queryForMap("select " + String.join(", ", COLUMNS) + " from events where id = ?", id);
        Map<String, Long> counts = new LinkedHashMap<>();
        COLUMNS.forEach(column -> counts.put(column, ((Number) row.get(column)).longValue()));
        return counts;
    }

    /** What {@link #counts} must be: zero everywhere except the given column/value pairs. */
    private Map<String, Long> only(Object... columnsAndValues) {
        Map<String, Long> expected = new LinkedHashMap<>();
        COLUMNS.forEach(column -> expected.put(column, 0L));
        for (int i = 0; i < columnsAndValues.length; i += 2) {
            expected.put((String) columnsAndValues[i], ((Number) columnsAndValues[i + 1]).longValue());
        }
        return expected;
    }

    private ResultActions click(UUID id, String body) throws Exception {
        return body == null ? mockMvc.perform(post("/api/v1/events/{id}/share-clicks", id))
                : mockMvc.perform(post("/api/v1/events/{id}/share-clicks", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions buy(UUID id, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{id}/share-buy-clicks", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions open(String code, String body) throws Exception {
        return body == null ? mockMvc.perform(post("/api/v1/share-links/{code}/open", code))
                : mockMvc.perform(post("/api/v1/share-links/{code}/open", code).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String codeOf(UUID id) throws Exception {
        String body = mockMvc.perform(get("/api/v1/events/{id}/share-link", id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"code\":\"([^\"]+)\".*", "$1");
    }

    // ---- clicks in the Share window ----

    @Test
    void eachShareAction_countsOnlyItsOwnCounter_withoutAToken() throws Exception {
        UUID id = event(EventStatus.ON_SALE);

        click(id, "{\"action\":\"SHARE_BUTTON\"}").andExpect(status().isNoContent());
        assertThat(counts(id)).isEqualTo(only("share_click_count", 1));

        click(id, "{\"action\":\"COPY_LINK\"}").andExpect(status().isNoContent());
        assertThat(counts(id)).isEqualTo(only("share_click_count", 1, "share_copy_count", 1));

        click(id, "{\"action\":\"QR_GENERATE\"}").andExpect(status().isNoContent());
        assertThat(counts(id)).isEqualTo(only("share_click_count", 1, "share_copy_count", 1, "share_qr_count", 1));
    }

    @Test
    void repeatedCalls_addUp() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        String code = codeOf(id);

        for (int i = 0; i < 3; i++) {
            click(id, "{\"action\":\"COPY_LINK\"}");
            open(code, "{\"source\":\"QR\"}");
            buy(id, "{\"source\":\"LINK\"}");
        }

        assertThat(counts(id)).isEqualTo(only("share_copy_count", 3, "share_qr_scan_count", 3, "share_link_buy_count", 3));
    }

    @Test
    void noBodyOrAnUnknownAction_countsAsTheShareButton() throws Exception {
        UUID id = event(EventStatus.PUBLISHED);

        click(id, null).andExpect(status().isNoContent());
        click(id, "{}").andExpect(status().isNoContent());
        click(id, "{\"action\":\"SOMETHING_ELSE\"}").andExpect(status().isNoContent());
        click(id, "{\"action\":null}").andExpect(status().isNoContent());

        assertThat(counts(id)).isEqualTo(only("share_click_count", 4));
    }

    @Test
    void clicks_areCountedForPublishedOnSaleAndSoldOut_butNotForADraftOrAnUnknownEvent_andAlwaysAnswer204() throws Exception {
        UUID published = event(EventStatus.PUBLISHED);
        UUID soldOut = event(EventStatus.SOLD_OUT);
        UUID draft = event(EventStatus.DRAFT);

        for (UUID id : List.of(published, soldOut, draft, UUID.randomUUID())) {
            click(id, "{\"action\":\"QR_GENERATE\"}").andExpect(status().isNoContent());
            click(id, null).andExpect(status().isNoContent());
            buy(id, "{\"source\":\"QR\"}").andExpect(status().isNoContent());
        }

        for (UUID id : List.of(published, soldOut)) {
            assertThat(counts(id)).isEqualTo(only("share_click_count", 1, "share_qr_count", 1, "share_qr_buy_count", 1));
        }
        assertThat(counts(draft)).isEqualTo(only());
    }

    @Test
    void thePublicEndpoints_setNoCookie() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        String code = codeOf(id);

        click(id, null).andExpect(header().doesNotExist("Set-Cookie"));
        buy(id, "{\"source\":\"LINK\"}").andExpect(header().doesNotExist("Set-Cookie"));
        open(code, null).andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(get("/api/v1/events/{id}/share-link", id)).andExpect(header().doesNotExist("Set-Cookie"));
    }

    // ---- opens of the shared link ----

    @Test
    void anOpen_countsALinkOpenOrAQrScan_andReturnsTheEvent_withoutAToken() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        String code = codeOf(id);

        open(code, "{\"source\":\"LINK\"}").andExpect(status().isOk()).andExpect(jsonPath("$.eventId").value(id.toString()));
        assertThat(counts(id)).isEqualTo(only("share_link_open_count", 1));

        open(code, "{\"source\":\"QR\"}").andExpect(status().isOk()).andExpect(jsonPath("$.eventId").value(id.toString()));
        assertThat(counts(id)).isEqualTo(only("share_link_open_count", 1, "share_qr_scan_count", 1));
    }

    @Test
    void noBodyOrAnUnknownSource_countsAsALinkOpen() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        String code = codeOf(id);

        open(code, null).andExpect(status().isOk());
        open(code, "{}").andExpect(status().isOk());
        open(code, "{\"source\":\"CARRIER_PIGEON\"}").andExpect(status().isOk());

        assertThat(counts(id)).isEqualTo(only("share_link_open_count", 3));
    }

    @Test
    void anUnknownCode_is404_andCountsNothing() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        codeOf(id);
        long before = jdbc.queryForObject("select coalesce(sum(share_link_open_count + share_qr_scan_count), 0) from events", Long.class);

        open("nosuchcd", "{\"source\":\"QR\"}").andExpect(status().isNotFound());
        open("nosuchcd", null).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select coalesce(sum(share_link_open_count + share_qr_scan_count), 0) from events", Long.class)).isEqualTo(before);
        assertThat(counts(id)).isEqualTo(only());
    }

    @Test
    void theLinkOfAnEventThatIsNoLongerPublic_is404_andCountsNothing() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        String code = codeOf(id);
        jdbc.update("update events set status = 'DRAFT' where id = ?", id);

        open(code, null).andExpect(status().isNotFound());

        assertThat(counts(id)).isEqualTo(only());
    }

    // ---- buy clicks from shared visits ----

    @Test
    void aBuyClick_countsOnlyItsSourcesBuyCounter() throws Exception {
        UUID id = event(EventStatus.ON_SALE);

        buy(id, "{\"source\":\"LINK\"}").andExpect(status().isNoContent());
        assertThat(counts(id)).isEqualTo(only("share_link_buy_count", 1));

        buy(id, "{\"source\":\"QR\"}").andExpect(status().isNoContent());
        assertThat(counts(id)).isEqualTo(only("share_link_buy_count", 1, "share_qr_buy_count", 1));
    }

    @Test
    void aBuyClickWithAnUnknownOrMissingSource_countsNothing_andAnswers204() throws Exception {
        UUID id = event(EventStatus.ON_SALE);

        buy(id, "{\"source\":\"CARRIER_PIGEON\"}").andExpect(status().isNoContent());
        buy(id, "{}").andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/events/{id}/share-buy-clicks", id)).andExpect(status().isNoContent());

        assertThat(counts(id)).isEqualTo(only());
    }

    // ---- the short link ----

    @Test
    void shareLink_createsAShortCodeOnFirstUse_andKeepsIt() throws Exception {
        UUID id = event(EventStatus.ON_SALE);

        String code = codeOf(id);

        assertThat(code).matches("[a-z2-9]{8}");
        mockMvc.perform(get("/api/v1/events/{id}/share-link", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.path").value("/s/" + code))
                .andExpect(jsonPath("$.code", hasLength(8)))
                .andExpect(jsonPath("$.path", matchesPattern("/s/[a-z2-9]{8}")));
        assertThat(codeOf(id)).isEqualTo(code);
    }

    @Test
    void shareLink_isNotFoundForADraftOrAnUnknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}/share-link", event(EventStatus.DRAFT))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/{id}/share-link", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ---- analytics ----

    @Test
    void analytics_showsEveryNumberToTheOrganizer_andIsRefusedToOthers() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        String code = codeOf(id);
        click(id, "{\"action\":\"SHARE_BUTTON\"}");
        click(id, "{\"action\":\"SHARE_BUTTON\"}");
        click(id, "{\"action\":\"COPY_LINK\"}");
        for (int i = 0; i < 3; i++) {
            click(id, "{\"action\":\"QR_GENERATE\"}");
        }
        for (int i = 0; i < 4; i++) {
            open(code, "{\"source\":\"LINK\"}");
        }
        for (int i = 0; i < 5; i++) {
            open(code, "{\"source\":\"QR\"}");
        }
        for (int i = 0; i < 6; i++) {
            buy(id, "{\"source\":\"LINK\"}");
        }
        for (int i = 0; i < 7; i++) {
            buy(id, "{\"source\":\"QR\"}");
        }

        mockMvc.perform(get("/api/v1/events/{id}/analytics", id).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareClicks").value(2))
                .andExpect(jsonPath("$.shareCopies").value(1))
                .andExpect(jsonPath("$.shareQrs").value(3))
                .andExpect(jsonPath("$.shareLinkOpens").value(4))
                .andExpect(jsonPath("$.shareQrScans").value(5))
                .andExpect(jsonPath("$.shareOpens").value(9))
                .andExpect(jsonPath("$.shareLinkBuys").value(6))
                .andExpect(jsonPath("$.shareQrBuys").value(7));
        mockMvc.perform(get("/api/v1/events/{id}/analytics", id).header("Authorization", stranger)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{id}/analytics", id).header("Authorization", customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{id}/analytics", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void savingTheEvent_doesNotResetTheCounters() throws Exception {
        UUID id = event(EventStatus.ON_SALE);
        Event stale = eventRepository.findById(id).orElseThrow();
        click(id, null);

        stale.setTitle("Renamed " + UUID.randomUUID());
        eventRepository.save(stale);

        assertThat(counts(id)).isEqualTo(only("share_click_count", 1));
    }
}
