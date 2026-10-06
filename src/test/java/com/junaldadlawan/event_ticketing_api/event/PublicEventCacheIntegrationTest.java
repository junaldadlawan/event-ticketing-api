package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.config.CacheConfig;
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
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public event list/detail caches against a real Postgres: they must
 * actually cache, must be cleared by every kind of write (service call,
 * direct repository save such as moderation does, a venue rename), and must
 * never hold a draft or anything caller-dependent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicEventCacheIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private CacheManager cacheManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdVenueIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();

    private User owner;
    private String ownerToken;
    private UUID orgId;
    private String uniqueTitle;

    @BeforeEach
    void setUp() {
        cache(CacheConfig.PUBLIC_EVENT).clear();
        cache(CacheConfig.PUBLIC_EVENT_SEARCH).clear();
        owner = User.builder().id(UUID.randomUUID()).email("owner-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        ownerToken = jwtService.generateAccessToken(owner);
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Cache Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(owner.getId()).documents(List.of()).build());
        orgId = organization.getId();
        createdOrgIds.add(orgId);
        OrganizationMember member = OrganizationMember.builder().userId(owner.getId()).organizationId(orgId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        createdMembers.add(organizationMemberRepository.save(member));
        uniqueTitle = "CacheTest-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        createdEventIds.forEach(eventRepository::deleteById);
        createdVenueIds.forEach(venueRepository::deleteById);
        createdMembers.forEach(member -> organizationMemberRepository
                .findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        createdOrgIds.forEach(organizationRepository::deleteById);
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }

    private long searchCacheSize() {
        return ((CaffeineCache) cache(CacheConfig.PUBLIC_EVENT_SEARCH)).getNativeCache().asMap().size();
    }

    private UUID persistVenue(String name) {
        Venue saved = venueRepository.save(Venue.builder().organizationId(orgId).name(name)
                .address("1 Test St").latitude(1.0).longitude(2.0).build());
        createdVenueIds.add(saved.getId());
        return saved.getId();
    }

    private UUID createEvent(UUID venueId) throws Exception {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        String venue = venueId == null ? "null" : "\"" + venueId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"organizationId":"%s","title":"%s","description":"desc","category":"music",
                                "venueId":%s,"startAt":"%s","endAt":"%s","timezone":"UTC"}
                                """.formatted(orgId, uniqueTitle, venue, start, start.plus(2, ChronoUnit.HOURS))))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdEventIds.add(id);
        return id;
    }

    private UUID createPublishedEvent(UUID venueId) throws Exception {
        UUID id = createEvent(venueId);
        mockMvc.perform(post("/api/v1/events/{id}/publish", id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        return id;
    }

    private String listPath() {
        return "/api/v1/events?keyword=" + uniqueTitle + "&size=100";
    }

    private static String idIn(UUID id) {
        return "$.content[?(@.id == '" + id + "')]";
    }

    @Test
    void detail_isServedFromTheCacheOnTheSecondRequest() throws Exception {
        UUID id = createPublishedEvent(null);
        assertThat(cache(CacheConfig.PUBLIC_EVENT).get(id)).isNull();

        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(uniqueTitle));
        assertThat(cache(CacheConfig.PUBLIC_EVENT).get(id)).as("cached after first read").isNotNull();

        // change the row behind the cache's back (plain SQL: no JPA callback, so no eviction)
        jdbcTemplate.update("update events set title = ? where id = ?", "changed-behind-the-cache", id);

        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(uniqueTitle)); // still the cached value
    }

    @Test
    void update_clearsTheCachedDetail() throws Exception {
        UUID id = createPublishedEvent(null);
        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isOk());
        assertThat(cache(CacheConfig.PUBLIC_EVENT).get(id)).isNotNull();

        mockMvc.perform(patch("/api/v1/events/{id}", id).header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json").content("{\"title\":\"Renamed " + uniqueTitle + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed " + uniqueTitle));
    }

    @Test
    void publish_makesTheEventAppearInAPreviouslyCachedList() throws Exception {
        UUID id = createEvent(null); // DRAFT
        mockMvc.perform(get(listPath())).andExpect(status().isOk()).andExpect(jsonPath(idIn(id)).isEmpty());
        assertThat(searchCacheSize()).as("empty list is cached too").isEqualTo(1);

        mockMvc.perform(post("/api/v1/events/{id}/publish", id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(listPath())).andExpect(status().isOk()).andExpect(jsonPath(idIn(id)).isNotEmpty());
    }

    @Test
    void cancel_updatesBothTheCachedDetailAndTheCachedList() throws Exception {
        UUID id = createPublishedEvent(null);
        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get(listPath())).andExpect(jsonPath(idIn(id)).isNotEmpty());

        mockMvc.perform(post("/api/v1/events/{id}/cancel", id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get(listPath())).andExpect(jsonPath(idIn(id)).isEmpty()); // list is PUBLISHED-only
    }

    /** Moderation suspends an event straight through the repository, not through EventService. */
    @Test
    void aDirectRepositorySave_alsoClearsTheCache() throws Exception {
        UUID id = createPublishedEvent(null);
        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(jsonPath("$.status").value("PUBLISHED"));

        var event = eventRepository.findById(id).orElseThrow();
        event.setStatus(EventStatus.SUSPENDED);
        eventRepository.save(event);

        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    /** The venue snapshot is embedded in cached event responses, so a venue rename must clear them. */
    @Test
    void aVenueRename_isReflectedInTheCachedEvent() throws Exception {
        UUID venueId = persistVenue("Old Venue Name");
        UUID id = createPublishedEvent(venueId);
        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(jsonPath("$.venue.name").value("Old Venue Name"));

        var venue = venueRepository.findById(venueId).orElseThrow();
        venue.setName("New Venue Name");
        venueRepository.save(venue);

        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(jsonPath("$.venue.name").value("New Venue Name"));
    }

    @Test
    void aDraftIsNeverCached_andStaysForbiddenToAnAnonymousCaller() throws Exception {
        UUID id = createEvent(null); // DRAFT

        mockMvc.perform(get("/api/v1/events/{id}", id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(cache(CacheConfig.PUBLIC_EVENT).get(id)).as("a draft must never be cached").isNull();

        mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isForbidden());
        assertThat(cache(CacheConfig.PUBLIC_EVENT).get(id)).isNull();
    }

    @Test
    void aMissingEvent_is404_andNotCached() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/events/{id}", missing)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/{id}", missing)).andExpect(status().isNotFound());
        assertThat(cache(CacheConfig.PUBLIC_EVENT).get(missing)).isNull();
    }

    @Test
    void theManagedListing_isNeverCached() throws Exception {
        createEvent(null);
        mockMvc.perform(get("/api/v1/events/managed").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        assertThat(searchCacheSize()).as("/events/managed must not populate the public search cache").isZero();
    }

    @Test
    void pageSize_isCappedAt100() throws Exception {
        mockMvc.perform(get("/api/v1/events?size=5000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }
}
