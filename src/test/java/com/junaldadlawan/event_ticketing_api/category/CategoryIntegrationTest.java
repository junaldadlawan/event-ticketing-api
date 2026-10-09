package com.junaldadlawan.event_ticketing_api.category;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.category.repository.CategoryRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end (real SecurityConfig, real Postgres, real cache) for the event
 * category list and for events being required to use one of its categories.
 * Only touches categories this test creates itself: the seeded defaults are
 * shared with every other test, so they are never deactivated here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    private final List<UUID> createdCategoryIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdEventIds.forEach(eventRepository::deleteById);
        createdEventIds.clear();
        createdMembers.forEach(member -> organizationMemberRepository
                .findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        createdMembers.clear();
        createdOrgIds.forEach(organizationRepository::deleteById);
        createdOrgIds.clear();
        createdCategoryIds.forEach(categoryRepository::deleteById);
        createdCategoryIds.clear();
    }

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .role(role)
                .build();
    }

    private String token(Role role) {
        return jwtService.generateAccessToken(inMemoryUser(role));
    }

    private UUID createCategory(String adminToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","description":"test category","sortOrder":999}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdCategoryIds.add(id);
        return id;
    }

    private List<String> publicCategoryNames() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk()).andReturn();
        List<String> names = new ArrayList<>();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            names.add(node.get("name").asText());
        }
        return names;
    }

    private UUID approvedOrgWithOwner(User owner) {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Category Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(owner.getId())
                .documents(List.of())
                .build());
        createdOrgIds.add(organization.getId());
        OrganizationMember member = OrganizationMember.builder()
                .userId(owner.getId())
                .organizationId(organization.getId())
                .build();
        member.getRoles().add(OrganizationRole.OWNER);
        createdMembers.add(organizationMemberRepository.save(member));
        return organization.getId();
    }

    private MvcResult postEvent(String ownerToken, UUID orgId, String category) throws Exception {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"organizationId":"%s","title":"Category Test Event","description":"desc","category":"%s",
                                "startAt":"%s","endAt":"%s","timezone":"UTC"}
                                """.formatted(orgId, category, startAt, startAt.plus(2, ChronoUnit.HOURS))))
                .andReturn();
    }

    private UUID trackEvent(MvcResult result) throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdEventIds.add(id);
        return id;
    }

    // ---- public read ----

    @Test
    void list_isPublic_andContainsTheSeededDefaults() throws Exception {
        List<String> names = publicCategoryNames();

        assertThat(names).contains("Music", "Sports", "Arts & Theatre", "Conference", "Festival", "Food & Drink",
                "Nightlife", "Community", "Workshop & Education", "Family", "Business & Networking", "Charity");
    }

    @Test
    void list_isOrderedBySortOrderThenName() throws Exception {
        List<String> names = publicCategoryNames();

        assertThat(names.indexOf("Music")).isLessThan(names.indexOf("Sports"));
        assertThat(names.indexOf("Sports")).isLessThan(names.indexOf("Charity"));
    }

    @Test
    void get_unknownId_is404() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ---- writes: access control ----

    @Test
    void writes_anonymous_areRejected() throws Exception {
        mockMvc.perform(post("/api/v1/categories").contentType("application/json").content("{\"name\":\"Nope\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/v1/categories/{id}", UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
        assertThat(publicCategoryNames()).doesNotContain("Nope");
    }

    @Test
    void writes_nonAdmin_areForbidden() throws Exception {
        String customer = token(Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + customer)
                        .contentType("application/json")
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/categories/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + customer)
                        .contentType("application/json")
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/categories/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
    }

    // ---- writes: admin lifecycle (+ cache eviction) ----

    @Test
    void admin_createRenameDeactivate_isReflectedInThePublicListImmediately() throws Exception {
        String admin = token(Role.ADMIN);
        String name = "ItCat " + UUID.randomUUID().toString().substring(0, 8);

        // Warm the cached list first, so the assertions below prove eviction, not a cold cache.
        assertThat(publicCategoryNames()).doesNotContain(name);

        UUID id = createCategory(admin, name);
        assertThat(publicCategoryNames()).contains(name);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"name\":\"%s\"}".formatted(name.toUpperCase())))
                .andExpect(status().isConflict());

        String renamed = name + " Renamed";
        mockMvc.perform(patch("/api/v1/categories/{id}", id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"name\":\"%s\"}".formatted(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed));
        assertThat(publicCategoryNames()).contains(renamed).doesNotContain(name);

        mockMvc.perform(delete("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(publicCategoryNames()).doesNotContain(renamed);
        mockMvc.perform(get("/api/v1/categories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void admin_createWithBlankName_is400() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token(Role.ADMIN))
                        .contentType("application/json")
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- events must use a category ----

    @Test
    void createEvent_acceptsAnyCaseOfAnActiveCategory_andStoresTheCanonicalName() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = approvedOrgWithOwner(owner);
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult result = postEvent(ownerToken, orgId, "mUsIc");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        UUID eventId = trackEvent(result);
        assertThat(eventRepository.findById(eventId).orElseThrow().getCategory()).isEqualTo("Music");
    }

    @Test
    void createEvent_unknownCategory_is400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = approvedOrgWithOwner(owner);

        MvcResult result = postEvent(jwtService.generateAccessToken(owner), orgId, "definitely not a category");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("Unknown event category");
    }

    @Test
    void updateEvent_toUnknownCategory_is400_andKnownCategoryIsCanonicalised() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = approvedOrgWithOwner(owner);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = trackEvent(postEvent(ownerToken, orgId, "music"));

        mockMvc.perform(patch("/api/v1/events/{id}", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"category\":\"nonsense\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/events/{id}", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"category\":\"sPoRtS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Sports"));
    }

    @Test
    void deactivatedCategory_isRefusedForNewEvents_butExistingEventsKeepIt() throws Exception {
        String admin = token(Role.ADMIN);
        String name = "ItCat " + UUID.randomUUID().toString().substring(0, 8);
        UUID categoryId = createCategory(admin, name);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = approvedOrgWithOwner(owner);
        String ownerToken = jwtService.generateAccessToken(owner);

        UUID eventId = trackEvent(postEvent(ownerToken, orgId, name));

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(postEvent(ownerToken, orgId, name).getResponse().getStatus()).isEqualTo(400);
        assertThat(eventRepository.findById(eventId).orElseThrow().getCategory()).isEqualTo(name);
    }
}
