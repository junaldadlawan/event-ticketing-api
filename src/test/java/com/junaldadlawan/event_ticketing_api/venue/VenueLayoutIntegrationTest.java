package com.junaldadlawan.event_ticketing_api.venue;

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
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The deprecated venue seat layout on the real database: saving, every validation rule, and who may read and write (copying seats now goes through organization layouts, see SeatLayoutIntegrationTest). */
@SpringBootTest
@AutoConfigureMockMvc
class VenueLayoutIntegrationTest {

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
    private VenueRepository venueRepository;
    @Autowired
    private EventRepository eventRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> venueIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();

    private UUID org;
    private UUID venue;
    private String owner;
    private String eventsOrganizer;
    private String plainMember;
    private String admin;
    private String stranger;
    private String customer;

    @BeforeEach
    void setUp() {
        User ownerUser = user(Role.CUSTOMER);
        User organizerUser = user(Role.CUSTOMER);
        User memberUser = user(Role.CUSTOMER);
        owner = token(ownerUser);
        eventsOrganizer = token(organizerUser);
        plainMember = token(memberUser);
        admin = token(user(Role.ADMIN));
        stranger = token(user(Role.CUSTOMER));
        customer = token(user(Role.CUSTOMER));
        org = organizationRepository.save(Organization.builder().name("Layout Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        orgIds.add(org);
        member(ownerUser, OrganizationRole.OWNER, null);
        member(organizerUser, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.EVENTS));
        member(memberUser, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.ANALYTICS));
        venue = venue();
    }

    @AfterEach
    void tearDown() {
        eventIds.forEach(id -> {
            jdbc.update("delete from seats where seat_map_id in (select id from seat_maps where event_id = ?)", id);
            jdbc.update("delete from seat_maps where event_id = ?", id);
            jdbc.update("delete from ticket_types where event_id = ?", id);
            jdbc.update("delete from audit_log_entries where event_id = ?", id);
            eventRepository.deleteById(id);
        });
        venueIds.forEach(id -> {
            jdbc.update("delete from venue_layouts where venue_id = ?", id);
            jdbc.update("delete from audit_log_entries where target_id = ?", id);
            venueRepository.deleteById(id);
        });
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(organizationRepository::deleteById);
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("layout-it-" + UUID.randomUUID() + "@test.local").role(role).build();
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

    private UUID venue() {
        UUID id = venueRepository.save(Venue.builder().organizationId(org).name("Layout Venue " + UUID.randomUUID()).build()).getId();
        venueIds.add(id);
        return id;
    }

    private UUID event(UUID venueId) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID id = eventRepository.save(Event.builder().organizationId(org).venueId(venueId).title("Layout Event " + UUID.randomUUID()).description("d")
                .category("music").status(EventStatus.DRAFT).ticketPrefix(freePrefix()).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC")
                .build()).getId();
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

    // ---- building layouts ----

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> seat(String number, double x, double y, String type) {
        return map("number", number, "x", x, "y", y, "type", type);
    }

    private static Map<String, Object> row(String label, Map<String, Object>... seats) {
        return map("label", label, "offset", 0, "seats", List.of(seats));
    }

    private static Map<String, Object> section(UUID id, String name, String kind, String color, double x, double y, Map<String, Object>... rows) {
        return map("id", id.toString(), "name", name, "kind", kind, "color", color, "x", x, "y", y, "rotation", 0, "rows", List.of(rows));
    }

    private static Map<String, Object> stage() {
        return map("type", "STAGE", "text", "Stage", "x", 100, "y", 10, "width", 300, "height", 60, "rotation", 0);
    }

    private static Map<String, Object> layout(List<Map<String, Object>> sections, Map<String, Object>... shapes) {
        return map("width", 1000, "height", 800, "sections", sections, "shapes", List.of(shapes));
    }

    /** VIP A: 2 seats for sale + 1 blocked + 1 wheelchair (3 for sale); Balcony: 2 seats. */
    private Map<String, Object> sampleLayout(UUID vip, UUID balcony) {
        return layout(List.of(
                section(vip, "VIP A", "VIP", "#c0392b", 100, 100,
                        row("A", seat("1", 0, 0, "SEAT"), seat("2", 20, 0, "SEAT"), seat("3", 40, 0, "BLOCKED"), seat("4", 60, 0, "WHEELCHAIR"))),
                section(balcony, "Balcony", "STANDARD", "#2980B9", 100, 300,
                        row("B", seat("1", 0, 0, "SEAT"), seat("2", 20, 0, "SEAT")))),
                stage());
    }

    private ResultActions putLayout(UUID venueId, String token, Object body) throws Exception {
        return mockMvc.perform(put("/api/v1/venues/{id}/layout", venueId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions getLayout(UUID venueId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/venues/{id}/layout", venueId).header("Authorization", token));
    }

    private void rejected(Object body, String message) throws Exception {
        putLayout(venue, owner, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString(message)));
    }

    // ---- reading and writing ----

    @Test
    void aVenueWithoutALayout_answersAnEmptyOne_toMembersAndAdmins() throws Exception {
        for (String allowed : List.of(owner, eventsOrganizer, plainMember, admin)) {
            getLayout(venue, allowed).andExpect(status().isOk()).andExpect(jsonPath("$.venueId").value(venue.toString()))
                    .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.sections.length()").value(0))
                    .andExpect(jsonPath("$.shapes.length()").value(0)).andExpect(jsonPath("$.width").isNumber()).andExpect(jsonPath("$.height").isNumber());
        }
    }

    @Test
    void theLayoutIsRefusedToStrangersAndAnonymous_andAnUnknownVenueIs404() throws Exception {
        getLayout(venue, stranger).andExpect(status().isForbidden());
        getLayout(venue, customer).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/venues/{id}/layout", venue)).andExpect(status().isUnauthorized());
        getLayout(UUID.randomUUID(), owner).andExpect(status().isNotFound());
    }

    @Test
    void saving_replacesTheWholeLayout_andEachSaveAddsOneToTheVersion() throws Exception {
        UUID vip = UUID.randomUUID();
        UUID balcony = UUID.randomUUID();

        putLayout(venue, owner, sampleLayout(vip, balcony)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.sections.length()").value(2)).andExpect(jsonPath("$.sections[0].color").value("#C0392B"));   // colours are stored upper-case
        getLayout(venue, plainMember).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.sections[0].name").value("VIP A"))
                .andExpect(jsonPath("$.sections[0].rows[0].seats.length()").value(4)).andExpect(jsonPath("$.shapes[0].type").value("STAGE"));

        putLayout(venue, eventsOrganizer, layout(List.of(section(vip, "Only VIP", "VIP", "#112233", 10, 10, row("Z", seat("1", 0, 0, "SEAT")))))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.sections.length()").value(1)).andExpect(jsonPath("$.shapes.length()").value(0));
        getLayout(venue, owner).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.sections[0].name").value("Only VIP"));
    }

    @Test
    void onlyTheOwnerOrAnOrganizerHoldingEvents_mayEdit() throws Exception {
        Map<String, Object> body = sampleLayout(UUID.randomUUID(), UUID.randomUUID());

        putLayout(venue, owner, body).andExpect(status().isOk());
        putLayout(venue, eventsOrganizer, body).andExpect(status().isOk());
        putLayout(venue, plainMember, body).andExpect(status().isForbidden());   // a member without EVENTS
        putLayout(venue, admin, body).andExpect(status().isForbidden());         // admins read, they do not edit
        putLayout(venue, stranger, body).andExpect(status().isForbidden());
        putLayout(UUID.randomUUID(), owner, body).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/venues/{id}/layout", venue).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        getLayout(venue, owner).andExpect(jsonPath("$.version").value(2));   // the refused saves changed nothing
    }

    @Test
    void aSave_isLoggedAsVenueLayoutUpdated_withNoSeatDetails() throws Exception {
        putLayout(venue, owner, sampleLayout(UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isOk());

        Map<String, Object> entry = jdbc.queryForMap("select action, organization_id, details::text details from audit_log_entries where target_id = ?", venue);
        assertThat(entry.get("action")).isEqualTo("venue.layout.updated");
        assertThat(entry.get("organization_id")).isEqualTo(org);
        JsonNode details = objectMapper.readTree((String) entry.get("details"));
        assertThat(details.get("version").asInt()).isEqualTo(1);
        assertThat(details.get("sections").asInt()).isEqualTo(2);
        assertThat(details.get("seats").asInt()).isEqualTo(6);
    }

    // ---- validation ----

    @Test
    void validation_sectionAndSeatLimits() throws Exception {
        List<Map<String, Object>> tooManySections = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooManySections.add(section(UUID.randomUUID(), "S" + i, "STANDARD", "#000000", 10, 10));
        }
        rejected(layout(tooManySections), "at most 50 sections");

        List<Map<String, Object>> seats = new ArrayList<>();
        for (int i = 0; i < 5001; i++) {
            seats.add(seat(String.valueOf(i), (i % 100) * 5, (i / 100) * 5, "SEAT"));
        }
        rejected(layout(List.of(map("id", UUID.randomUUID().toString(), "name", "Big", "kind", "STANDARD", "color", "#000000", "x", 0, "y", 0,
                "rows", List.of(map("label", "A", "seats", seats))))), "at most 5000 seats");

        // exactly 50 sections and exactly 5000 seats are fine
        List<Map<String, Object>> fifty = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            fifty.add(section(UUID.randomUUID(), "S" + i, "STANDARD", "#000000", 10, 10));
        }
        putLayout(venue, owner, layout(fifty)).andExpect(status().isOk());
        List<Map<String, Object>> exactly = new ArrayList<>(seats.subList(0, 5000));
        putLayout(venue, owner, layout(List.of(map("id", UUID.randomUUID().toString(), "name", "Big", "kind", "STANDARD", "color", "#000000", "x", 0, "y", 0,
                "rows", List.of(map("label", "A", "seats", exactly)))))).andExpect(status().isOk());
    }

    @Test
    void validation_uniqueSectionNames_andUniqueRowAndSeatInASection() throws Exception {
        rejected(layout(List.of(section(UUID.randomUUID(), "VIP", "VIP", "#000000", 10, 10), section(UUID.randomUUID(), " vip ", "VIP", "#000000", 20, 20))),
                "section names must be unique");
        UUID same = UUID.randomUUID();
        rejected(layout(List.of(section(same, "A", "VIP", "#000000", 10, 10), section(same, "B", "VIP", "#000000", 20, 20))), "used twice");
        rejected(layout(List.of(section(UUID.randomUUID(), "A", "VIP", "#000000", 10, 10,
                row("A", seat("1", 0, 0, "SEAT")), row("A", seat("1", 10, 0, "SEAT"))))), "used twice in the section");
        rejected(layout(List.of(section(UUID.randomUUID(), "A", "VIP", "#000000", 10, 10, row("A", seat("1", 0, 0, "SEAT"), seat("1", 10, 0, "SEAT"))))),
                "used twice in the section");

        // the same seat number in another row, or the same row and number in another section, is fine
        putLayout(venue, owner, layout(List.of(
                section(UUID.randomUUID(), "A", "VIP", "#000000", 10, 10, row("A", seat("1", 0, 0, "SEAT")), row("B", seat("1", 0, 10, "SEAT"))),
                section(UUID.randomUUID(), "B", "VIP", "#000000", 10, 100, row("A", seat("1", 0, 0, "SEAT")))))).andExpect(status().isOk());
    }

    @Test
    void validation_coordinatesStayInsideThePlan() throws Exception {
        rejected(layout(List.of(section(UUID.randomUUID(), "A", "VIP", "#000000", 1001, 10))), "x must be between");
        rejected(layout(List.of(section(UUID.randomUUID(), "A", "VIP", "#000000", 10, -1))), "y must be between");
        rejected(layout(List.of(section(UUID.randomUUID(), "A", "VIP", "#000000", 990, 10, row("A", seat("1", 50, 0, "SEAT"))))), "outside the plan");
        rejected(layout(List.of(section(UUID.randomUUID(), "A", "VIP", "#000000", 10, 790, row("A", seat("1", 0, 50, "SEAT"))))), "outside the plan");
        rejected(layout(List.of(), map("type", "STAGE", "text", "x", "x", 900, "y", 10, "width", 200, "height", 50)), "reaches outside the plan");

        // a section turned a quarter makes its row run downwards: the seats are checked where they end up
        Map<String, Object> turned = section(UUID.randomUUID(), "A", "VIP", "#000000", 100, 700, row("A", seat("1", 0, 0, "SEAT"), seat("2", 200, 0, "SEAT")));
        turned.put("rotation", 90);
        rejected(layout(List.of(turned)), "outside the plan");
    }

    @Test
    void validation_everythingElseIsRefusedWithAClearMessage() throws Exception {
        UUID id = UUID.randomUUID();
        rejected(layout(List.of(section(id, "A", "VIP", "red", 10, 10))), "color must be a #RRGGBB colour");
        rejected(layout(List.of(section(id, "A", "VIP", "#12345", 10, 10))), "color must be a #RRGGBB colour");
        rejected(layout(List.of(section(id, " ", "VIP", "#000000", 10, 10))), "name must not be blank");
        rejected(layout(List.of(section(id, "<b>A</b>", "VIP", "#000000", 10, 10))), "must not contain HTML");
        rejected(layout(List.of(section(id, "A".repeat(101), "VIP", "#000000", 10, 10))), "at most 100 characters");
        rejected(layout(List.of(section(id, "A", "VIP", "#000000", 10, 10, row("R".repeat(21), seat("1", 0, 0, "SEAT"))))), "at most 20 characters");
        rejected(layout(List.of(section(id, "A", "VIP", "#000000", 10, 10, row("A", seat(" ", 0, 0, "SEAT"))))), "number must not be blank");
        rejected(map("width", 0, "height", 800, "sections", List.of()), "width is required and must be between");
        rejected(map("width", 1000, "sections", List.of()), "height is required");
        rejected(map("width", 1000, "height", 800), "sections is required");
        rejected(layout(List.of(), map("type", "LABEL", "x", 10, "y", 10)), "text is required for a LABEL");
        rejected(layout(List.of(), map("type", "WALL", "x", 10, "y", 10)), "width and height are required");
        Map<String, Object> noKind = section(id, "A", "VIP", "#000000", 10, 10);
        noKind.remove("kind");
        rejected(layout(List.of(noKind)), "kind is required");
        Map<String, Object> noSeatNumber = map("x", 1, "y", 1);
        rejected(layout(List.of(section(id, "A", "VIP", "#000000", 10, 10, row("A", noSeatNumber)))), "number must not be blank");
        putLayout(venue, owner, layout(List.of(section(id, "A", "BOGUS", "#000000", 10, 10)))).andExpect(status().isBadRequest());   // an unknown kind
        putLayout(venue, owner, layout(List.of(section(id, "A", "VIP", "#000000", 10, 10, row("A", seat("1", 0, 0, "SOFA")))))).andExpect(status().isBadRequest());

        getLayout(venue, owner).andExpect(jsonPath("$.version").value(0));   // nothing was saved by any of these
    }
}
