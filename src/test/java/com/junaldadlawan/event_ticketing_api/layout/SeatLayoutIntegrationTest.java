package com.junaldadlawan.event_ticketing_api.layout;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Organization seat layouts and preset layouts on the real database: CRUD, who may do what, deleting, copying presets, and copying seats into events. */
@SpringBootTest
@AutoConfigureMockMvc
class SeatLayoutIntegrationTest {

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
    private final List<UUID> presetIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();

    private UUID org;
    private String owner;
    private String eventsOrganizer;
    private String plainMember;
    private String admin;
    private String stranger;

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
        org = organization(ownerUser);
        member(org, ownerUser, OrganizationRole.OWNER, null);
        member(org, organizerUser, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.EVENTS));
        member(org, memberUser, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.ANALYTICS));
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
        orgIds.forEach(id -> {
            jdbc.update("delete from audit_log_entries where target_id in (select id from seat_layouts where organization_id = ?)", id);
            jdbc.update("delete from seat_layouts where organization_id = ?", id);
        });
        presetIds.forEach(id -> {
            jdbc.update("delete from audit_log_entries where target_id = ?", id);
            jdbc.update("delete from layout_presets where id = ?", id);
        });
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(organizationRepository::deleteById);
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("seat-layout-it-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private UUID organization(User ownerUser) {
        UUID id = organizationRepository.save(Organization.builder().name("Seat Layout Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        orgIds.add(id);
        return id;
    }

    private void member(UUID orgId, User user, OrganizationRole role, Set<OrganizationPermission> permissions) {
        OrganizationMember member = OrganizationMember.builder().userId(user.getId()).organizationId(orgId).build();
        member.getRoles().add(role);
        if (permissions != null) {
            member.choosePermissions(permissions);
        }
        members.add(organizationMemberRepository.save(member));
    }

    private UUID event(UUID orgId, Instant start) {
        UUID id = eventRepository.save(Event.builder().organizationId(orgId).title("Seat Layout Event " + UUID.randomUUID()).description("d")
                .category("music").status(EventStatus.DRAFT).ticketPrefix(freePrefix()).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC")
                .build()).getId();
        eventIds.add(id);
        return id;
    }

    private UUID event() {
        return event(org, Instant.now().plus(10, ChronoUnit.DAYS));
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

    private static Map<String, Object> layout(String name, List<Map<String, Object>> sections, Map<String, Object>... shapes) {
        return map("name", name, "width", 1000, "height", 800, "sections", sections, "shapes", List.of(shapes));
    }

    /** VIP A: 2 seats + 1 blocked + 1 wheelchair (3 for sale); Balcony: 2 seats. */
    private static Map<String, Object> sampleLayout(String name, UUID vip, UUID balcony) {
        return layout(name, List.of(
                section(vip, "VIP A", "VIP", "#c0392b", 100, 100,
                        row("A", seat("1", 0, 0, "SEAT"), seat("2", 20, 0, "SEAT"), seat("3", 40, 0, "BLOCKED"), seat("4", 60, 0, "WHEELCHAIR"))),
                section(balcony, "Balcony", "STANDARD", "#2980B9", 100, 300,
                        row("B", seat("1", 0, 0, "SEAT"), seat("2", 20, 0, "SEAT")))),
                stage());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID createLayout(Map<String, Object> body) throws Exception {
        return UUID.fromString(body(send(post("/api/v1/organizations/{id}/layouts", org), owner, body).andExpect(status().isCreated())).get("id").asString());
    }

    private UUID createPreset(Map<String, Object> body) throws Exception {
        UUID id = UUID.fromString(body(send(post("/api/v1/layout-presets"), admin, body).andExpect(status().isCreated())).get("id").asString());
        presetIds.add(id);
        return id;
    }

    // ---- organization layouts ----

    @Test
    void create_list_get_replace_andTheVersionGoesUpOnEachReplace() throws Exception {
        UUID vip = UUID.randomUUID();
        JsonNode created = body(send(post("/api/v1/organizations/{id}/layouts", org), eventsOrganizer, sampleLayout("  Main Hall ", vip, UUID.randomUUID()))
                .andExpect(status().isCreated()));
        UUID id = UUID.fromString(created.get("id").asString());
        assertThat(created.get("name").asString()).isEqualTo("Main Hall");
        assertThat(created.get("version").asInt()).isEqualTo(1);
        assertThat(created.get("seatCount").asInt()).isEqualTo(6);
        assertThat(created.get("organizationId").asString()).isEqualTo(org.toString());

        send(get("/api/v1/organizations/{id}/layouts", org), plainMember, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Main Hall"))
                .andExpect(jsonPath("$[0].seatCount").value(6)).andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].width").value(1000.0)).andExpect(jsonPath("$[0].sections").doesNotExist());
        send(get("/api/v1/layouts/{id}", id), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].color").value("#C0392B")).andExpect(jsonPath("$.shapes[0].type").value("STAGE"));

        send(put("/api/v1/layouts/{id}", id), owner, layout("Main Hall v2", List.of(section(vip, "Only", "STANDARD", "#000000", 10, 10,
                row("A", seat("1", 0, 0, "SEAT")))))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.name").value("Main Hall v2")).andExpect(jsonPath("$.seatCount").value(1));
        send(get("/api/v1/layouts/{id}", id), owner, null).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.sections.length()").value(1));

        List<String> actions = jdbc.queryForList("select action from audit_log_entries where target_id = ? order by created_at", String.class, id);
        assertThat(actions).containsExactly("layout.created", "layout.updated");
    }

    @Test
    void namesAreUniqueInTheOrganization_ignoringCase_butNotAcrossOrganizations() throws Exception {
        UUID first = createLayout(sampleLayout("Arena", UUID.randomUUID(), UUID.randomUUID()));
        UUID second = createLayout(sampleLayout("Club", UUID.randomUUID(), UUID.randomUUID()));

        send(post("/api/v1/organizations/{id}/layouts", org), owner, sampleLayout("ARENA", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isConflict());
        send(put("/api/v1/layouts/{id}", second), owner, sampleLayout("arena", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isConflict());
        send(put("/api/v1/layouts/{id}", first), owner, sampleLayout("arena", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isOk());   // its own name

        User otherOwner = user(Role.CUSTOMER);
        UUID otherOrg = organization(otherOwner);
        member(otherOrg, otherOwner, OrganizationRole.OWNER, null);
        send(post("/api/v1/organizations/{id}/layouts", otherOrg), token(otherOwner), sampleLayout("Arena", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isCreated());

        send(delete("/api/v1/layouts/{id}", first), owner, null).andExpect(status().isNoContent());
        createLayout(sampleLayout("Arena", UUID.randomUUID(), UUID.randomUUID()));   // a deleted layout's name is free again
    }

    @Test
    void nameAndLayoutAreValidated() throws Exception {
        send(post("/api/v1/organizations/{id}/layouts", org), owner, sampleLayout(" ", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isBadRequest());
        send(post("/api/v1/organizations/{id}/layouts", org), owner, sampleLayout("x".repeat(101), UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isBadRequest());
        send(post("/api/v1/organizations/{id}/layouts", org), owner, sampleLayout("<b>", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isBadRequest());
        Map<String, Object> outside = layout("Outside", List.of(section(UUID.randomUUID(), "A", "VIP", "#000000", 10, 10, row("A", seat("1", 5000, 0, "SEAT")))));
        send(post("/api/v1/organizations/{id}/layouts", org), owner, outside).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from seat_layouts where organization_id = ?", Integer.class, org)).isZero();
    }

    @Test
    void membersAndAdminsRead_onlyTheOwnerOrAnEventsOrganizerWrites() throws Exception {
        UUID id = createLayout(sampleLayout("Hall", UUID.randomUUID(), UUID.randomUUID()));

        for (String reader : List.of(owner, eventsOrganizer, plainMember, admin)) {
            send(get("/api/v1/organizations/{id}/layouts", org), reader, null).andExpect(status().isOk());
            send(get("/api/v1/layouts/{id}", id), reader, null).andExpect(status().isOk());
        }
        send(get("/api/v1/organizations/{id}/layouts", org), stranger, null).andExpect(status().isForbidden());
        send(get("/api/v1/layouts/{id}", id), stranger, null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/layouts/{id}", id)).andExpect(status().isUnauthorized());

        for (String notEditor : List.of(plainMember, admin, stranger)) {
            send(post("/api/v1/organizations/{id}/layouts", org), notEditor, sampleLayout("New " + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                    .andExpect(status().isForbidden());
            send(put("/api/v1/layouts/{id}", id), notEditor, sampleLayout("Hall", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isForbidden());
            send(delete("/api/v1/layouts/{id}", id), notEditor, null).andExpect(status().isForbidden());
        }
        send(get("/api/v1/layouts/{id}", UUID.randomUUID()), owner, null).andExpect(status().isNotFound());
        send(get("/api/v1/organizations/{id}/layouts", UUID.randomUUID()), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void delete_isSoft_andRefusedWhileAnUpcomingEventUsesTheLayout() throws Exception {
        UUID vip = UUID.randomUUID();
        UUID id = createLayout(sampleLayout("Used", vip, UUID.randomUUID()));
        UUID eventId = event();
        createTicketType(eventId, map("layoutId", id.toString(), "sectionId", vip.toString())).andExpect(status().isCreated());

        send(delete("/api/v1/layouts/{id}", id), owner, null).andExpect(status().isConflict());

        // once the event has ended, the layout may go
        jdbc.update("update events set start_time = now() - interval '3 hours', end_time = now() - interval '1 hour' where id = ?", eventId);
        send(delete("/api/v1/layouts/{id}", id), eventsOrganizer, null).andExpect(status().isNoContent());

        send(get("/api/v1/layouts/{id}", id), owner, null).andExpect(status().isNotFound());
        send(get("/api/v1/organizations/{id}/layouts", org), owner, null).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("select deleted_at is not null from seat_layouts where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select action from audit_log_entries where target_id = ? order by created_at desc limit 1", String.class, id))
                .isEqualTo("layout.deleted");
    }

    // ---- presets ----

    @Test
    void presets_onlyAdminsManageThem_organizersSeeTheActiveOnes() throws Exception {
        UUID active = createPreset(withPreset(sampleLayout("Preset Active " + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), "Theatre", null));
        UUID hidden = createPreset(withPreset(sampleLayout("Preset Hidden " + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), null, false));

        JsonNode listed = body(send(get("/api/v1/layout-presets"), plainMember, null).andExpect(status().isOk()));
        List<String> ids = new ArrayList<>();
        listed.forEach(preset -> ids.add(preset.get("id").asString()));
        assertThat(ids).contains(active.toString()).doesNotContain(hidden.toString());
        send(get("/api/v1/layout-presets/{id}", active), owner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Theatre")).andExpect(jsonPath("$.sections.length()").value(2));
        send(get("/api/v1/layout-presets/{id}", hidden), owner, null).andExpect(status().isNotFound());
        send(get("/api/v1/layout-presets/{id}", hidden), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        send(get("/api/v1/layout-presets"), stranger, null).andExpect(status().isForbidden());   // not an owner or organizer anywhere
        send(post("/api/v1/layout-presets"), owner, sampleLayout("Mine", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isForbidden());
        send(put("/api/v1/layout-presets/{id}", active), eventsOrganizer, sampleLayout("Mine", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isForbidden());
        send(delete("/api/v1/layout-presets/{id}", active), owner, null).andExpect(status().isForbidden());

        send(put("/api/v1/layout-presets/{id}", active), admin, sampleLayout("Preset Renamed " + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.active").value(true));
        send(delete("/api/v1/layout-presets/{id}", active), admin, null).andExpect(status().isNoContent());
        send(get("/api/v1/layout-presets/{id}", active), admin, null).andExpect(status().isNotFound());

        List<String> actions = jdbc.queryForList("select action from audit_log_entries where target_id = ? order by created_at", String.class, active);
        assertThat(actions).containsExactly("layout_preset.created", "layout_preset.updated", "layout_preset.deleted");
    }

    @Test
    void copyingAPreset_makesAnIndependentOrganizationLayout() throws Exception {
        String name = "Preset Copy " + UUID.randomUUID().toString().substring(0, 8);
        UUID preset = createPreset(sampleLayout(name, UUID.randomUUID(), UUID.randomUUID()));

        JsonNode copy = body(send(post("/api/v1/organizations/{id}/layouts/from-preset/{presetId}", org, preset), eventsOrganizer, null)
                .andExpect(status().isCreated()));
        UUID copyId = UUID.fromString(copy.get("id").asString());
        assertThat(copy.get("name").asString()).isEqualTo(name);
        assertThat(copy.get("version").asInt()).isEqualTo(1);
        assertThat(copy.get("seatCount").asInt()).isEqualTo(6);
        assertThat(jdbc.queryForObject("select details->>'presetId' from audit_log_entries where target_id = ? and action = 'layout.created'", String.class, copyId))
                .isEqualTo(preset.toString());

        // editing the preset never changes the copy, and the copy is the organization's to edit
        send(put("/api/v1/layout-presets/{id}", preset), admin, layout(name, List.of())).andExpect(status().isOk());
        send(get("/api/v1/layouts/{id}", copyId), owner, null).andExpect(jsonPath("$.sections.length()").value(2));
        send(put("/api/v1/layouts/{id}", copyId), owner, layout(name, List.of())).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        send(post("/api/v1/organizations/{id}/layouts/from-preset/{presetId}", org, preset), owner, null).andExpect(status().isConflict());   // name taken
        send(post("/api/v1/organizations/{id}/layouts/from-preset/{presetId}", org, preset), plainMember, null).andExpect(status().isForbidden());
        send(post("/api/v1/organizations/{id}/layouts/from-preset/{presetId}", org, UUID.randomUUID()), owner, null).andExpect(status().isNotFound());

        UUID inactive = createPreset(withPreset(sampleLayout("Preset Off " + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), null, false));
        send(post("/api/v1/organizations/{id}/layouts/from-preset/{presetId}", org, inactive), owner, null).andExpect(status().isNotFound());
    }

    private static Map<String, Object> withPreset(Map<String, Object> body, String description, Boolean active) {
        body.put("description", description);
        body.put("active", active);
        return body;
    }

    // ---- copying seats into an event ----

    private ResultActions createTicketType(UUID eventId, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = map("name", "Seats " + UUID.randomUUID().toString().substring(0, 6), "kind", "RESERVED_SEATING",
                "price", map("amount", 5000, "currency", "PHP"), "saleEndAt", Instant.now().plus(5, ChronoUnit.DAYS).toString());
        body.putAll(extra);
        return send(post("/api/v1/events/{id}/ticket-types", eventId), owner, body);
    }

    private JsonNode seatmap(UUID eventId) throws Exception {
        return body(send(get("/api/v1/events/{id}/seatmap", eventId), owner, null).andExpect(status().isOk()));
    }

    @Test
    void aTicketTypeForASection_copiesItsSeatsForSale_andTheEventRemembersTheLayout() throws Exception {
        UUID vip = UUID.randomUUID();
        UUID layoutId = createLayout(sampleLayout("Copy", vip, UUID.randomUUID()));
        UUID eventId = event();

        JsonNode ticketType = body(createTicketType(eventId, map("layoutId", layoutId.toString(), "sectionId", vip.toString())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityTotal").value(3)).andExpect(jsonPath("$.quantityAvailable").value(3)));   // the blocked seat is not for sale

        JsonNode map = seatmap(eventId);
        assertThat(map.get("seats")).hasSize(3);
        assertThat(map.get("width").asDouble()).isEqualTo(1000.0);
        assertThat(map.get("shapes").get(0).get("type").asString()).isEqualTo("STAGE");
        List<String> numbers = new ArrayList<>();
        for (JsonNode seat : map.get("seats")) {
            numbers.add(seat.get("seatNumber").asString());
            assertThat(seat.get("section").asString()).isEqualTo("VIP A");
            assertThat(seat.get("sectionId").asString()).isEqualTo(vip.toString());
            assertThat(seat.get("sectionColor").asString()).isEqualTo("#C0392B");
            assertThat(seat.get("ticketTypeId").asString()).isEqualTo(ticketType.get("id").asString());
            assertThat(seat.get("y").asDouble()).isEqualTo(100.0);
            assertThat(seat.get("x").asDouble()).isEqualTo(Map.of("1", 100.0, "2", 120.0, "4", 160.0).get(seat.get("seatNumber").asString()));
        }
        assertThat(numbers).containsExactlyInAnyOrder("1", "2", "4");
        assertThat(jdbc.queryForObject("select layout_id from events where id = ?", UUID.class, eventId)).isEqualTo(layoutId);
    }

    @Test
    void aTicketTypeForTheWholeLayout_copiesEverySection() throws Exception {
        UUID layoutId = createLayout(sampleLayout("Whole", UUID.randomUUID(), UUID.randomUUID()));
        UUID eventId = event();

        createTicketType(eventId, map("layoutId", layoutId.toString(), "wholeLayout", true, "quantityTotal", 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityTotal").value(5));

        assertThat(seatmap(eventId).get("seats")).hasSize(5);
    }

    @Test
    void editingTheLayoutLater_neverChangesAnEventThatCopiedIt_andCannotBeMixedIn() throws Exception {
        UUID vip = UUID.randomUUID();
        UUID balcony = UUID.randomUUID();
        UUID layoutId = createLayout(sampleLayout("Edited", vip, balcony));
        UUID eventId = event();
        createTicketType(eventId, map("layoutId", layoutId.toString(), "sectionId", vip.toString())).andExpect(status().isCreated());
        createTicketType(eventId, map("layoutId", layoutId.toString(), "sectionId", vip.toString())).andExpect(status().isConflict());   // section taken
        String before = seatmap(eventId).toString();

        send(put("/api/v1/layouts/{id}", layoutId), owner, layout("Edited", List.of(section(vip, "Renamed", "STANDARD", "#00FF00", 500, 500,
                row("Q", seat("9", 0, 0, "SEAT"))), section(balcony, "Balcony", "STANDARD", "#2980B9", 100, 300, row("B", seat("1", 0, 0, "SEAT"))))))
                .andExpect(status().isOk());

        assertThat(seatmap(eventId).toString()).isEqualTo(before);
        createTicketType(eventId, map("layoutId", layoutId.toString(), "sectionId", balcony.toString())).andExpect(status().isConflict());   // version 2 now
    }

    @Test
    void anEventUsesOneLayout_andOnlyOneOfItsOwnOrganization() throws Exception {
        UUID vip = UUID.randomUUID();
        UUID first = createLayout(sampleLayout("First", vip, UUID.randomUUID()));
        UUID otherVip = UUID.randomUUID();
        UUID second = createLayout(sampleLayout("Second", otherVip, UUID.randomUUID()));
        UUID eventId = event();
        createTicketType(eventId, map("layoutId", first.toString(), "sectionId", vip.toString())).andExpect(status().isCreated());

        createTicketType(eventId, map("layoutId", second.toString(), "sectionId", otherVip.toString())).andExpect(status().isConflict());

        User otherOwner = user(Role.CUSTOMER);
        UUID otherOrg = organization(otherOwner);
        member(otherOrg, otherOwner, OrganizationRole.OWNER, null);
        UUID foreign = UUID.fromString(body(send(post("/api/v1/organizations/{id}/layouts", otherOrg), token(otherOwner),
                sampleLayout("Foreign", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isCreated())).get("id").asString());
        createTicketType(event(), map("layoutId", foreign.toString(), "wholeLayout", true)).andExpect(status().isBadRequest());
    }

    @Test
    void theRulesForCopying_areEnforced() throws Exception {
        UUID vip = UUID.randomUUID();
        UUID layoutId = createLayout(sampleLayout("Rules", vip, UUID.randomUUID()));
        UUID eventId = event();
        String id = layoutId.toString();

        createTicketType(eventId, map("kind", "GENERAL_ADMISSION", "layoutId", id, "sectionId", vip.toString())).andExpect(status().isBadRequest());
        createTicketType(eventId, map("layoutId", id, "sectionId", vip.toString(), "wholeLayout", true)).andExpect(status().isBadRequest());
        createTicketType(eventId, map("layoutId", id)).andExpect(status().isBadRequest());                         // neither sectionId nor wholeLayout
        createTicketType(eventId, map("sectionId", vip.toString())).andExpect(status().isBadRequest());           // no layoutId
        createTicketType(eventId, map("layoutId", id, "sectionId", UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        createTicketType(eventId, map("layoutId", UUID.randomUUID().toString(), "wholeLayout", true)).andExpect(status().isBadRequest());
        createTicketType(eventId, map()).andExpect(status().isBadRequest());                                      // no quantity and no layout

        UUID blocked = UUID.randomUUID();
        UUID closed = createLayout(layout("Closed", List.of(section(blocked, "Closed", "STANDARD", "#000000", 10, 10, row("A", seat("1", 0, 0, "BLOCKED"))))));
        createTicketType(eventId, map("layoutId", closed.toString(), "sectionId", blocked.toString())).andExpect(status().isBadRequest());   // nothing for sale

        assertThat(jdbc.queryForObject("select count(*) from ticket_types where event_id = ?", Integer.class, eventId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from seat_maps where event_id = ?", Integer.class, eventId)).isZero();
        assertThat(jdbc.queryForObject("select layout_id from events where id = ?", UUID.class, eventId)).isNull();
    }

    @Test
    void aReservedTypeWithoutALayout_stillWorksAsBefore() throws Exception {
        UUID eventId = event();

        createTicketType(eventId, map("quantityTotal", 40)).andExpect(status().isCreated()).andExpect(jsonPath("$.quantityTotal").value(40));

        assertThat(jdbc.queryForObject("select count(*) from seat_maps where event_id = ?", Integer.class, eventId)).isZero();
    }
}
