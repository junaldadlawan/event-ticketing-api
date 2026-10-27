package com.junaldadlawan.event_ticketing_api.venue;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The extra venue details on the real database: create, partial update, clearing, and the checks on country, capacity and type. */
@SpringBootTest
@AutoConfigureMockMvc
class VenueDetailsIntegrationTest {

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

    private final List<UUID> venueIds = new ArrayList<>();
    private OrganizationMember ownerMember;
    private UUID org;
    private String owner;

    @BeforeEach
    void setUp() {
        User ownerUser = User.builder().id(UUID.randomUUID()).email("venue-details-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        owner = "Bearer " + jwtService.generateAccessToken(ownerUser);
        org = organizationRepository.save(Organization.builder().name("Venue Details Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        OrganizationMember member = OrganizationMember.builder().userId(ownerUser.getId()).organizationId(org).build();
        member.getRoles().add(OrganizationRole.OWNER);
        ownerMember = organizationMemberRepository.save(member);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from venue_layouts where venue_id in (select id from venues where organization_id = ?)", org);
        jdbc.update("delete from venues where organization_id = ?", org);
        organizationMemberRepository.findByUserIdAndOrganizationId(ownerMember.getUserId(), org).ifPresent(organizationMemberRepository::delete);
        organizationRepository.deleteById(org);
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{id}/venues", org).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions patchRaw(UUID id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/venues/{id}", id).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID created(Map<String, Object> body) throws Exception {
        String response = create(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asString());
        venueIds.add(id);
        return id;
    }

    private JsonNode venue(UUID id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/venues/{id}", id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> full() {
        return map("name", "Details Hall", "venueType", "CONCERT_HALL", "country", "ph", "province", "  Metro Manila ", "city", "Taguig",
                "address", "7th Ave cor. 28th St, Bonifacio Global City", "latitude", 14.55, "longitude", 121.05, "description", "A big hall.",
                "maxCapacity", 1200);
    }

    @Test
    void create_withAllFields_returnsThemInCamelCase_trimmed_andTheCountryUpperCased() throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(create(full()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.venueType").value("CONCERT_HALL")).andExpect(jsonPath("$.country").value("PH"))
                .andExpect(jsonPath("$.province").value("Metro Manila")).andExpect(jsonPath("$.city").value("Taguig"))
                .andExpect(jsonPath("$.address").value("7th Ave cor. 28th St, Bonifacio Global City")).andExpect(jsonPath("$.latitude").value(14.55))
                .andExpect(jsonPath("$.description").value("A big hall.")).andExpect(jsonPath("$.maxCapacity").value(1200))
                .andExpect(jsonPath("$.coverImageUrl").doesNotExist()).andReturn().getResponse().getContentAsString()).get("id").asString());
        venueIds.add(id);

        JsonNode read = venue(id);
        assertThat(read.get("venueType").asString()).isEqualTo("CONCERT_HALL");
        assertThat(read.get("country").asString()).isEqualTo("PH");
        assertThat(read.get("maxCapacity").asInt()).isEqualTo(1200);
        assertThat(read.has("coverImageUrl")).isFalse();
    }

    @Test
    void aVenueWithOnlyAName_hasNullDetails_andAVirtualVenueNeedsNoAddressPositionOrCapacity() throws Exception {
        UUID plain = created(map("name", "Plain Venue"));
        UUID virtual = created(map("name", "Online Stage", "venueType", "VIRTUAL", "city", "  ", "description", ""));

        for (String field : List.of("venueType", "country", "province", "city", "description", "maxCapacity", "address", "latitude", "longitude")) {
            assertThat(venue(plain).get(field).isNull()).as(field).isTrue();
        }
        JsonNode read = venue(virtual);
        assertThat(read.get("venueType").asString()).isEqualTo("VIRTUAL");
        assertThat(read.get("city").isNull()).isTrue();          // blank is null
        assertThat(read.get("description").isNull()).isTrue();
        assertThat(read.get("address").isNull()).isTrue();
        assertThat(read.get("maxCapacity").isNull()).isTrue();
    }

    @Test
    void aPartialUpdate_changesOnlyTheFieldsSent() throws Exception {
        UUID id = created(full());
        JsonNode before = venue(id);

        patchRaw(id, "{\"city\":\"Makati\",\"maxCapacity\":900}").andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Makati")).andExpect(jsonPath("$.maxCapacity").value(900))
                .andExpect(jsonPath("$.coverImageUrl").doesNotExist());

        JsonNode after = venue(id);
        for (String field : List.of("name", "venueType", "country", "province", "address", "latitude", "longitude", "description")) {
            assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
        }
        assertThat(after.get("city").asString()).isEqualTo("Makati");
        assertThat(after.get("maxCapacity").asInt()).isEqualTo(900);
    }

    @Test
    void anUpdate_canChangeTheTypeAndCountry_normalisingThem() throws Exception {
        UUID id = created(map("name", "Changing Venue"));

        patchRaw(id, "{\"venueType\":\" stadium \",\"country\":\" ph \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.venueType").value("STADIUM")).andExpect(jsonPath("$.country").value("PH"));
    }

    @Test
    void sendingNull_clearsAField_andABlankStringClearsToo_whileLeavingOneOutKeepsIt() throws Exception {
        UUID id = created(full());

        patchRaw(id, "{\"venueType\":null,\"country\":null,\"province\":null,\"city\":\"  \",\"description\":null,\"maxCapacity\":null}")
                .andExpect(status().isOk());

        JsonNode read = venue(id);
        for (String field : List.of("venueType", "country", "province", "city", "description", "maxCapacity")) {
            assertThat(read.get(field).isNull()).as(field).isTrue();
        }
        assertThat(read.get("name").asString()).isEqualTo("Details Hall");   // left out = unchanged
        assertThat(read.get("address").asString()).startsWith("7th Ave");
        assertThat(read.get("latitude").asDouble()).isEqualTo(14.55);
    }

    @Test
    void anEmptyAddressClearsIt_forAVenueMovedOnline() throws Exception {
        UUID id = created(full());

        patchRaw(id, "{\"venueType\":\"VIRTUAL\",\"address\":\"\"}").andExpect(status().isOk());

        assertThat(venue(id).get("address").isNull()).isTrue();
    }

    @Test
    void anUnknownOrNotEnabledCountryCode_isRefused() throws Exception {
        UUID id = created(map("name", "Country Venue", "country", "PH"));

        // US is a real ISO code, but not a country the platform has enabled
        for (String bad : List.of("ZZ", "XX", "PHL", "P", "Philippines", "12", "US")) {
            create(map("name", "Bad", "country", bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("not a supported country")));
            patchRaw(id, "{\"country\":\"" + bad + "\"}").andExpect(status().isBadRequest());
        }
        assertThat(venue(id).get("country").asString()).isEqualTo("PH");   // refused updates changed nothing
    }

    @Test
    void aCapacityBelowOne_isRefused() throws Exception {
        UUID id = created(map("name", "Capacity Venue", "maxCapacity", 10));

        for (int bad : new int[] {0, -5}) {
            create(map("name", "Bad", "maxCapacity", bad)).andExpect(status().isBadRequest());
            patchRaw(id, "{\"maxCapacity\":" + bad + "}").andExpect(status().isBadRequest());
        }
        create(map("name", "One", "maxCapacity", 1)).andExpect(status().isCreated());
        assertThat(venue(id).get("maxCapacity").asInt()).isEqualTo(10);
    }

    @Test
    void aBadTypeOrText_isRefused() throws Exception {
        UUID id = created(map("name", "Checked Venue"));

        create(map("name", "Bad", "venueType", "CASTLE")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("venueType must be one of")));
        patchRaw(id, "{\"venueType\":\"CASTLE\"}").andExpect(status().isBadRequest());
        create(map("name", "Bad", "city", "x".repeat(101))).andExpect(status().isBadRequest());
        create(map("name", "Bad", "province", "x".repeat(101))).andExpect(status().isBadRequest());
        create(map("name", "Bad", "description", "x".repeat(2001))).andExpect(status().isBadRequest());
        create(map("name", "Bad", "description", "<script>")).andExpect(status().isBadRequest());
        patchRaw(id, "{\"description\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
        patchRaw(id, "{\"city\":\"<b>\"}").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from venues where organization_id = ?", Integer.class, org)).isEqualTo(1);   // only the one made above
    }

    @Test
    void theCapacity_andTheSeatsOfALayout_areBothReturned_withoutBeingCheckedAgainstEachOther() throws Exception {
        UUID id = created(map("name", "Small Hall", "maxCapacity", 2));
        List<Map<String, Object>> seats = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            seats.add(map("number", String.valueOf(i), "x", i * 10, "y", 0, "type", "SEAT"));
        }
        Map<String, Object> layout = map("width", 1000, "height", 800, "shapes", List.of(), "sections", List.of(map("id", UUID.randomUUID().toString(),
                "name", "Main", "kind", "STANDARD", "color", "#112233", "x", 10, "y", 10, "rows", List.of(map("label", "A", "seats", seats)))));

        mockMvc.perform(put("/api/v1/venues/{id}/layout", id).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(layout)))
                .andExpect(status().isOk());

        assertThat(venue(id).get("maxCapacity").asInt()).isEqualTo(2);
        mockMvc.perform(get("/api/v1/venues/{id}/layout", id).header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].rows[0].seats.length()").value(5));
    }
}
