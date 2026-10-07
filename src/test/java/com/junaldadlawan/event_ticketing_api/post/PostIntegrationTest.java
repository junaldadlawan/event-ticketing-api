package com.junaldadlawan.event_ticketing_api.post;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.post.entity.Post;
import com.junaldadlawan.event_ticketing_api.post.enums.PostKind;
import com.junaldadlawan.event_ticketing_api.post.repository.PostRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end for sales and announcements through the real security chain and Postgres: anyone reads, only an admin
 * writes or removes, and posts of events that are not public (draft, cancelled, deleted) are never listed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
        "app.upload.dir=target/test-uploads",
        "app.upload.public-base-url="
})
class PostIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    private final List<UUID> postIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> orgIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        postIds.forEach(postRepository::deleteById);
        postIds.clear();
        eventIds.forEach(eventRepository::deleteById);
        eventIds.clear();
        orgIds.forEach(organizationRepository::deleteById);
        orgIds.clear();
    }

    private String token(Role role) {
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(UUID.randomUUID())
                .email("post-" + UUID.randomUUID() + "@test.local").role(role).build());
    }

    private UUID org() {
        Organization saved = organizationRepository.save(Organization.builder().name("Post Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private UUID event(EventStatus status, boolean deleted) {
        Instant start = Instant.now().plus(20, ChronoUnit.DAYS);
        Event event = Event.builder().organizationId(org()).title("Post Event " + UUID.randomUUID()).description("d").category("music")
                .status(status).ticketPrefix("P" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build();
        if (deleted) {
            event.markDeleted();
        }
        Event saved = eventRepository.save(event);
        eventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID seed(UUID eventId, String title) {
        Post saved = postRepository.save(Post.builder().eventId(eventId).kind(PostKind.SALE).title(title).body("b").build());
        postIds.add(saved.getId());
        return saved.getId();
    }

    private UUID createViaApi(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        postIds.add(id);
        return id;
    }

    private JsonNode list(String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/posts" + query)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("content").forEach(node -> titles.add(node.get("title").asText()));
        return titles;
    }

    // ---- create ----

    @Test
    void create_asAdmin_returns201_andTheSiteWidePostIsListedForAnonymous() throws Exception {
        String title = "Site news " + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN)).contentType("application/json")
                        .content("{\"kind\":\"ANNOUNCEMENT\",\"title\":\"  " + title + "  \",\"body\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.eventId").isEmpty())
                .andExpect(jsonPath("$.eventTitle").isEmpty())
                .andExpect(jsonPath("$.kind").value("ANNOUNCEMENT"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.body").value("Hello"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andDo(result -> postIds.add(UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText())));

        assertThat(titles(list(""))).contains(title);
    }

    @Test
    void create_forAnEvent_returnsTheEventTitle() throws Exception {
        UUID eventId = event(EventStatus.PUBLISHED, false);
        String eventTitle = eventRepository.findById(eventId).orElseThrow().getTitle();

        UUID id = createViaApi("{\"eventId\":\"" + eventId + "\",\"kind\":\"SALE\",\"title\":\"Early bird\"}");

        mockMvc.perform(get("/api/v1/posts").param("eventId", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].eventTitle").value(eventTitle))
                .andExpect(jsonPath("$.content[0].body").value(""));
    }

    @Test
    void create_asNonAdminOrAnonymous_isRejected_andNothingIsStored() throws Exception {
        long before = postRepository.count();
        String body = "{\"kind\":\"SALE\",\"title\":\"nope\"}";

        mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.CUSTOMER)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/posts").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());

        assertThat(postRepository.count()).isEqualTo(before);
    }

    @Test
    void create_forAnUnknownOrDeletedEvent_returns404() throws Exception {
        UUID deleted = event(EventStatus.PUBLISHED, true);

        for (UUID eventId : new UUID[] {UUID.randomUUID(), deleted}) {
            mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN)).contentType("application/json")
                            .content("{\"eventId\":\"" + eventId + "\",\"kind\":\"SALE\",\"title\":\"x\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void create_invalidBodies_return400() throws Exception {
        for (String body : new String[] {
                "{\"kind\":\"SALE\"}",
                "{\"title\":\"x\"}",
                "{\"kind\":\"SALE\",\"title\":\"   \"}",
                "{\"kind\":\"SALE\",\"title\":\"" + "T".repeat(121) + "\"}",
                "{\"kind\":\"SALE\",\"title\":\"ok\",\"body\":\"" + "B".repeat(2001) + "\"}",
                "{\"kind\":\"SALE\",\"title\":\"<script>alert(1)</script>\"}",
                "{\"kind\":\"SALE\",\"title\":\"ok\",\"body\":\"<img src=x onerror=alert(1)>\"}",
                "{\"kind\":\"BANNER\",\"title\":\"ok\"}"}) {
            mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void create_titleAndBodyAtTheirLimits_areAccepted() throws Exception {
        createViaApi("{\"kind\":\"SALE\",\"title\":\"" + "T".repeat(120) + "\",\"body\":\"" + "B".repeat(2000) + "\"}");
    }

    // ---- list ----

    @Test
    void list_isPublic_newestFirst_andShowsBothSiteWideAndPublishedEventPosts() throws Exception {
        UUID published = event(EventStatus.PUBLISHED, false);
        String older = "older " + UUID.randomUUID();
        String newer = "newer " + UUID.randomUUID();
        seed(null, older);
        Thread.sleep(15);
        seed(published, newer);

        List<String> titles = titles(list(""));

        assertThat(titles).contains(older, newer);
        assertThat(titles.indexOf(newer)).isLessThan(titles.indexOf(older));
    }

    @Test
    void list_siteWide_leavesOutEventPosts_andEventIdLeavesOutEverythingElse() throws Exception {
        UUID eventId = event(EventStatus.ON_SALE, false);
        UUID otherEvent = event(EventStatus.PUBLISHED, false);
        String siteWide = "sw " + UUID.randomUUID();
        String mine = "mine " + UUID.randomUUID();
        String theirs = "theirs " + UUID.randomUUID();
        seed(null, siteWide);
        seed(eventId, mine);
        seed(otherEvent, theirs);

        List<String> siteWideOnly = titles(list("?siteWide=true&size=50"));
        List<String> forEvent = titles(list("?eventId=" + eventId));

        assertThat(siteWideOnly).contains(siteWide).doesNotContain(mine, theirs);
        assertThat(forEvent).containsExactly(mine);
    }

    @Test
    void list_postsOfNonPublicEvents_areNeverListed() throws Exception {
        UUID draft = event(EventStatus.DRAFT, false);
        UUID cancelled = event(EventStatus.CANCELLED, false);
        UUID deleted = event(EventStatus.PUBLISHED, true);
        UUID soldOut = event(EventStatus.SOLD_OUT, false);
        UUID completed = event(EventStatus.COMPLETED, false);
        String draftTitle = "draft " + UUID.randomUUID();
        String cancelledTitle = "cancelled " + UUID.randomUUID();
        String deletedTitle = "deleted " + UUID.randomUUID();
        String soldOutTitle = "soldout " + UUID.randomUUID();
        String completedTitle = "completed " + UUID.randomUUID();
        seed(draft, draftTitle);
        seed(cancelled, cancelledTitle);
        seed(deleted, deletedTitle);
        seed(soldOut, soldOutTitle);
        seed(completed, completedTitle);

        List<String> all = titles(list("?size=50"));

        assertThat(all).contains(soldOutTitle, completedTitle).doesNotContain(draftTitle, cancelledTitle, deletedTitle);
        assertThat(list("?eventId=" + draft).get("content")).isEmpty();
        assertThat(list("?eventId=" + cancelled).get("content")).isEmpty();
        assertThat(list("?eventId=" + deleted).get("content")).isEmpty();
        assertThat(titles(list("?eventId=" + soldOut))).containsExactly(soldOutTitle);
    }

    @Test
    void list_pageSizeDefaultsTo20_andIsCappedAt50() throws Exception {
        assertThat(list("").get("size").asInt()).isEqualTo(20);
        assertThat(list("?size=500").get("size").asInt()).isEqualTo(50);
        assertThat(list("?size=3").get("size").asInt()).isEqualTo(3);
    }

    @Test
    void list_pagesThroughAnEventsPosts() throws Exception {
        UUID eventId = event(EventStatus.PUBLISHED, false);
        for (int i = 0; i < 3; i++) {
            seed(eventId, "page " + i);
            Thread.sleep(5);
        }

        JsonNode first = list("?eventId=" + eventId + "&size=2&page=0");
        JsonNode second = list("?eventId=" + eventId + "&size=2&page=1");

        assertThat(first.get("totalElements").asInt()).isEqualTo(3);
        assertThat(titles(first)).containsExactly("page 2", "page 1");
        assertThat(titles(second)).containsExactly("page 0");
        assertThat(second.get("last").asBoolean()).isTrue();
    }

    @Test
    void list_eventIdTogetherWithSiteWide_returns400_andABadUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("eventId", UUID.randomUUID().toString()).param("siteWide", "true"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts").param("eventId", "nope")).andExpect(status().isBadRequest());
    }

    // ---- delete ----

    @Test
    void delete_asAdmin_returns204_softDeletes_andTheSecondDeleteIs404() throws Exception {
        String title = "to delete " + UUID.randomUUID();
        UUID id = seed(null, title);

        mockMvc.perform(delete("/api/v1/posts/{id}", id).header("Authorization", token(Role.ADMIN))).andExpect(status().isNoContent());

        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
        assertThat(postRepository.findById(id)).get().satisfies(row -> assertThat(row.getDeletedAt()).isNotNull());
        mockMvc.perform(delete("/api/v1/posts/{id}", id).header("Authorization", token(Role.ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    void delete_unknownPost_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/{id}", UUID.randomUUID()).header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_asNonAdminOrAnonymous_isRejected_andThePostStays() throws Exception {
        String title = "keep " + UUID.randomUUID();
        UUID id = seed(null, title);

        mockMvc.perform(delete("/api/v1/posts/{id}", id).header("Authorization", token(Role.CUSTOMER))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/posts/{id}", id)).andExpect(status().isUnauthorized());

        assertThat(titles(list("?siteWide=true&size=50"))).contains(title);
        assertThat(postRepository.findById(id)).get().satisfies(row -> assertThat(row.getDeletedAt()).isNull());
    }

    // ---- schedule, expiry and hiding ----

    private UUID seedWindow(String title, Instant publishAt, Instant expiresAt, boolean hidden) {
        Post saved = postRepository.save(Post.builder().kind(PostKind.SALE).title(title).body("b")
                .publishAt(publishAt).expiresAt(expiresAt).hidden(hidden).build());
        postIds.add(saved.getId());
        return saved.getId();
    }

    private JsonNode listAll(String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/posts?all=true" + query).header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String statusOf(JsonNode page, String title) {
        for (JsonNode node : page.get("content")) {
            if (node.get("title").asText().equals(title)) {
                return node.get("status").asText();
            }
        }
        return null;
    }

    private void patch(UUID id, String body, int expectedStatus) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/posts/{id}", id)
                        .header("Authorization", token(Role.ADMIN)).contentType("application/json").content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void create_scheduledPost_isNotPublicUntilItsTime_butAdminsSeeItAsScheduled() throws Exception {
        String title = "scheduled " + UUID.randomUUID();
        Instant publishAt = Instant.now().plus(1, ChronoUnit.DAYS);

        createViaApi("{\"kind\":\"SALE\",\"title\":\"" + title + "\",\"publishAt\":\"" + publishAt + "\"}");

        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
        assertThat(statusOf(listAll("&siteWide=true&size=50"), title)).isEqualTo("SCHEDULED");
    }

    @Test
    void aScheduledPost_goesLiveByItself_whenItsTimeComes() throws Exception {
        String title = "soon " + UUID.randomUUID();
        seedWindow(title, Instant.now().plusMillis(1500), null, false);

        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
        Thread.sleep(1700);

        assertThat(titles(list("?siteWide=true&size=50"))).contains(title);
        assertThat(statusOf(listAll("&siteWide=true&size=50"), title)).isEqualTo("LIVE");
    }

    @Test
    void anExpiredPost_leavesThePublicList_butAdminsStillSeeItAsExpired() throws Exception {
        String title = "expired " + UUID.randomUUID();
        seedWindow(title, null, Instant.now().minusSeconds(5), false);

        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
        assertThat(statusOf(listAll("&siteWide=true&size=50"), title)).isEqualTo("EXPIRED");
    }

    @Test
    void aPostThatExpiresLater_isPublicNow_andShowsItsExpiry() throws Exception {
        String title = "expires later " + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        createViaApi("{\"kind\":\"SALE\",\"title\":\"" + title + "\",\"expiresAt\":\"" + expiresAt + "\"}");

        JsonNode page = list("?siteWide=true&size=50");
        assertThat(titles(page)).contains(title);
        for (JsonNode node : page.get("content")) {
            if (node.get("title").asText().equals(title)) {
                assertThat(Instant.parse(node.get("expiresAt").asText())).isEqualTo(expiresAt);
                assertThat(node.get("hidden").asBoolean()).isFalse();
                assertThat(node.get("status").asText()).isEqualTo("LIVE");
            }
        }
    }

    @Test
    void create_hiddenPost_isNeverPublic_untilUnhidden() throws Exception {
        String title = "hidden " + UUID.randomUUID();

        UUID id = createViaApi("{\"kind\":\"SALE\",\"title\":\"" + title + "\",\"hidden\":true}");

        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
        assertThat(statusOf(listAll("&siteWide=true&size=50"), title)).isEqualTo("HIDDEN");

        patch(id, "{\"hidden\":false}", 200);
        assertThat(titles(list("?siteWide=true&size=50"))).contains(title);

        patch(id, "{\"hidden\":true}", 200);
        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
    }

    @Test
    void aHiddenPostStaysHidden_evenInsideItsPublishWindow() throws Exception {
        String title = "hidden in window " + UUID.randomUUID();
        seedWindow(title, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), true);

        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
        assertThat(statusOf(listAll("&siteWide=true&size=50"), title)).isEqualTo("HIDDEN");
    }

    @Test
    void hidingAnEventPost_removesItFromTheEventsList() throws Exception {
        UUID eventId = event(EventStatus.PUBLISHED, false);
        UUID id = createViaApi("{\"eventId\":\"" + eventId + "\",\"kind\":\"SALE\",\"title\":\"Early bird\"}");
        assertThat(list("?eventId=" + eventId).get("content")).hasSize(1);

        patch(id, "{\"hidden\":true}", 200);

        assertThat(list("?eventId=" + eventId).get("content")).isEmpty();
        assertThat(listAll("&eventId=" + eventId).get("content")).hasSize(1);
    }

    @Test
    void update_canEndAPostNow_andScheduleOrClearDates() throws Exception {
        String title = "to end " + UUID.randomUUID();
        UUID id = createViaApi("{\"kind\":\"SALE\",\"title\":\"" + title + "\"}");
        assertThat(titles(list("?siteWide=true&size=50"))).contains(title);

        patch(id, "{\"expiresAt\":\"" + Instant.now().minusSeconds(1) + "\"}", 200);
        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);
        assertThat(statusOf(listAll("&siteWide=true&size=50"), title)).isEqualTo("EXPIRED");

        patch(id, "{\"clearExpiresAt\":true}", 200);
        assertThat(titles(list("?siteWide=true&size=50"))).contains(title);

        patch(id, "{\"publishAt\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}", 200);
        assertThat(titles(list("?siteWide=true&size=50"))).doesNotContain(title);

        patch(id, "{\"clearPublishAt\":true}", 200);
        assertThat(titles(list("?siteWide=true&size=50"))).contains(title);
    }

    @Test
    void update_changesOnlyTheGivenFields_andReturnsThePost() throws Exception {
        UUID id = createViaApi("{\"kind\":\"SALE\",\"title\":\"before\",\"body\":\"keep this\"}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/posts/{id}", id)
                        .header("Authorization", token(Role.ADMIN)).contentType("application/json")
                        .content("{\"title\":\"  after  \",\"kind\":\"ANNOUNCEMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("after"))
                .andExpect(jsonPath("$.kind").value("ANNOUNCEMENT"))
                .andExpect(jsonPath("$.body").value("keep this"))
                .andExpect(jsonPath("$.status").value("LIVE"));
    }

    @Test
    void createAndUpdate_withAnInvalidWindow_return400() throws Exception {
        for (String body : new String[] {
                "{\"kind\":\"SALE\",\"title\":\"x\",\"expiresAt\":\"" + Instant.now().minusSeconds(60) + "\"}",
                "{\"kind\":\"SALE\",\"title\":\"x\",\"publishAt\":\"" + Instant.now().plus(2, ChronoUnit.DAYS)
                        + "\",\"expiresAt\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}",
                "{\"kind\":\"SALE\",\"title\":\"x\",\"publishAt\":\"not a date\"}"}) {
            mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        UUID id = createViaApi("{\"kind\":\"SALE\",\"title\":\"window\",\"publishAt\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}");
        patch(id, "{\"expiresAt\":\"" + Instant.now().plusSeconds(60) + "\"}", 400);
        patch(id, "{\"clearPublishAt\":true,\"publishAt\":\"" + Instant.now().plusSeconds(60) + "\"}", 400);
        patch(id, "{\"title\":\"  \"}", 400);
    }

    @Test
    void update_asNonAdminOrAnonymous_isRejected_andUnknownPostIs404() throws Exception {
        UUID id = seed(null, "patch me " + UUID.randomUUID());
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder asCustomer =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/posts/{id}", id)
                        .header("Authorization", token(Role.CUSTOMER)).contentType("application/json").content("{\"hidden\":true}");

        mockMvc.perform(asCustomer).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/posts/{id}", id)
                        .contentType("application/json").content("{\"hidden\":true}"))
                .andExpect(status().isUnauthorized());
        patch(UUID.randomUUID(), "{\"hidden\":true}", 404);
        assertThat(postRepository.findById(id)).get().satisfies(row -> assertThat(row.isHidden()).isFalse());
    }

    @Test
    void list_allTrue_isForAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("all", "true").header("Authorization", token(Role.CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/posts").param("all", "true")).andExpect(status().isForbidden());
    }

    @Test
    void list_allTrue_alsoShowsPostsOfNonPublicEvents_andNeverDeletedOnes() throws Exception {
        UUID draft = event(EventStatus.DRAFT, false);
        String draftTitle = "draft event post " + UUID.randomUUID();
        seed(draft, draftTitle);
        String deletedTitle = "deleted " + UUID.randomUUID();
        UUID deletedId = seed(null, deletedTitle);
        mockMvc.perform(delete("/api/v1/posts/{id}", deletedId).header("Authorization", token(Role.ADMIN))).andExpect(status().isNoContent());

        assertThat(titles(list("?size=50"))).doesNotContain(draftTitle);
        assertThat(titles(listAll("&size=50"))).contains(draftTitle).doesNotContain(deletedTitle);
        assertThat(statusOf(listAll("&eventId=" + draft), draftTitle)).isEqualTo("LIVE");
    }

    @Test
    void list_isOrderedByWhenAPostWentLive_notWhenItWasWritten() throws Exception {
        String backdated = "backdated " + UUID.randomUUID();
        String plain = "plain " + UUID.randomUUID();
        seed(null, plain);
        Thread.sleep(15);
        // written after "plain", but published a day earlier, so it sits below it
        seedWindow(backdated, Instant.now().minus(1, ChronoUnit.DAYS), null, false);

        List<String> titles = titles(list("?siteWide=true&size=50"));

        assertThat(titles).contains(plain, backdated);
        assertThat(titles.indexOf(plain)).isLessThan(titles.indexOf(backdated));
    }

    // ---- picture ----

    private String upload() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/uploads")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "post.png", "image/png", out.toByteArray()))
                        .header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
    }

    private int fileStatus(String url) throws Exception {
        return mockMvc.perform(get(java.net.URI.create(url).getPath())).andReturn().getResponse().getStatus();
    }

    private UUID createWithImage(String title, String imageUrl) throws Exception {
        return createViaApi("{\"kind\":\"SALE\",\"title\":\"" + title + "\",\"imageUrl\":\"" + imageUrl + "\"}");
    }

    private void patchImage(UUID id, String imageUrl, int expectedStatus) throws Exception {
        patch(id, "{\"imageUrl\":\"" + imageUrl + "\"}", expectedStatus);
    }

    private String imageOf(String title, boolean admin) throws Exception {
        JsonNode page = admin ? listAll("&siteWide=true&size=50") : list("?siteWide=true&size=50");
        for (JsonNode node : page.get("content")) {
            if (node.get("title").asText().equals(title)) {
                return node.get("imageUrl").isNull() ? null : node.get("imageUrl").asText();
            }
        }
        throw new AssertionError("post not listed: " + title);
    }

    @Test
    void create_withAnOwnUploadedImage_returnsIt_andThePublicAndAdminListsShowIt() throws Exception {
        String url = upload();
        String title = "with picture " + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN)).contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"" + title + "\",\"imageUrl\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(url))
                .andDo(result -> postIds.add(UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText())));

        assertThat(imageOf(title, false)).isEqualTo(url);
        assertThat(imageOf(title, true)).isEqualTo(url);
        assertThat(fileStatus(url)).isEqualTo(200);
    }

    @Test
    void create_withoutAPicture_orABlankOne_hasANullImageUrl() throws Exception {
        String plain = "plain " + UUID.randomUUID();
        String blank = "blank " + UUID.randomUUID();
        createViaApi("{\"kind\":\"SALE\",\"title\":\"" + plain + "\"}");
        createViaApi("{\"kind\":\"SALE\",\"title\":\"" + blank + "\",\"imageUrl\":\"  \"}");

        assertThat(imageOf(plain, false)).isNull();
        assertThat(imageOf(blank, false)).isNull();
    }

    @Test
    void create_withALinkThatIsNotOurUpload_returns400WithAReadableMessage_andStoresNothing() throws Exception {
        String own = upload();
        String fileName = own.substring(own.lastIndexOf('/') + 1);
        long before = postRepository.count();

        for (String bad : new String[] {
                "https://evil.example.com/pixel.png",
                "http://evil.example.com/api/v1/uploads/files/" + fileName,
                "http://localhost/api/v1/uploads/files/" + fileName + "?x=1",
                "http://localhost/api/v1/uploads/files/" + fileName + "#frag",
                "http://localhost/api/v1/uploads/files/../" + fileName,
                "http://localhost/api/v1/uploads/files/not-a-stored-name.png",
                "http://localhost/api/v1/uploads/files/" + UUID.randomUUID() + ".png",
                "javascript:alert(1)"}) {
            mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN)).contentType("application/json")
                            .content("{\"kind\":\"SALE\",\"title\":\"x\",\"imageUrl\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("imageUrl")));
        }

        assertThat(postRepository.count()).isEqualTo(before);
        assertThat(fileStatus(own)).isEqualTo(200);
    }

    @Test
    void create_withAnImageUrlOver500Characters_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/posts").header("Authorization", token(Role.ADMIN)).contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"x\",\"imageUrl\":\"http://localhost/" + "a".repeat(500) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_replacingThePicture_returnsTheNewOne_andDeletesTheOldFile() throws Exception {
        String first = upload();
        String second = upload();
        String title = "replace " + UUID.randomUUID();
        UUID id = createWithImage(title, first);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/posts/{id}", id)
                        .header("Authorization", token(Role.ADMIN)).contentType("application/json")
                        .content("{\"imageUrl\":\"" + second + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(second));

        assertThat(imageOf(title, false)).isEqualTo(second);
        assertThat(fileStatus(first)).isEqualTo(404);
        assertThat(fileStatus(second)).isEqualTo(200);
    }

    @Test
    void update_replacingThePicture_keepsTheOldFileWhileAnotherPostStillUsesIt() throws Exception {
        String shared = upload();
        String replacement = upload();
        UUID id = createWithImage("shared one " + UUID.randomUUID(), shared);
        createWithImage("shared two " + UUID.randomUUID(), shared);

        patchImage(id, replacement, 200);

        assertThat(fileStatus(shared)).isEqualTo(200);
    }

    @Test
    void update_removingThePicture_withAnEmptyString_deletesTheFile() throws Exception {
        String url = upload();
        String title = "remove " + UUID.randomUUID();
        UUID id = createWithImage(title, url);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/posts/{id}", id)
                        .header("Authorization", token(Role.ADMIN)).contentType("application/json").content("{\"imageUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").isEmpty());

        assertThat(imageOf(title, false)).isNull();
        assertThat(fileStatus(url)).isEqualTo(404);
    }

    @Test
    void update_leavingTheImageUrlOutOrSendingTheSameOne_keepsThePictureAndItsFile() throws Exception {
        String url = upload();
        String title = "keep " + UUID.randomUUID();
        UUID id = createWithImage(title, url);

        patch(id, "{\"title\":\"" + title + " edited\"}", 200);
        patchImage(id, url, 200);
        patch(id, "{\"imageUrl\":null}", 200);

        assertThat(imageOf(title + " edited", false)).isEqualTo(url);
        assertThat(fileStatus(url)).isEqualTo(200);
    }

    @Test
    void update_withALinkThatIsNotOurUpload_returns400_andKeepsTheOldPicture() throws Exception {
        String url = upload();
        String title = "stay " + UUID.randomUUID();
        UUID id = createWithImage(title, url);

        patchImage(id, "https://evil.example.com/pixel.png", 400);
        patchImage(id, "http://localhost/api/v1/uploads/files/" + UUID.randomUUID() + ".png", 400);

        assertThat(imageOf(title, false)).isEqualTo(url);
        assertThat(fileStatus(url)).isEqualTo(200);
    }

    @Test
    void delete_aPost_releasesItsPictureFile_unlessAnotherPostStillUsesIt() throws Exception {
        String alone = upload();
        String shared = upload();
        UUID aloneId = createWithImage("alone " + UUID.randomUUID(), alone);
        UUID sharedId = createWithImage("shared a " + UUID.randomUUID(), shared);
        createWithImage("shared b " + UUID.randomUUID(), shared);

        mockMvc.perform(delete("/api/v1/posts/{id}", aloneId).header("Authorization", token(Role.ADMIN))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/posts/{id}", sharedId).header("Authorization", token(Role.ADMIN))).andExpect(status().isNoContent());

        assertThat(fileStatus(alone)).isEqualTo(404);
        assertThat(fileStatus(shared)).isEqualTo(200);
    }

    @Test
    void aSoftDeletedPostDoesNotKeepAFileAlive() throws Exception {
        String url = upload();
        UUID gone = createWithImage("gone " + UUID.randomUUID(), url);
        UUID stays = createWithImage("stays " + UUID.randomUUID(), url);
        mockMvc.perform(delete("/api/v1/posts/{id}", gone).header("Authorization", token(Role.ADMIN))).andExpect(status().isNoContent());
        assertThat(fileStatus(url)).isEqualTo(200);

        patchImage(stays, "", 200);

        assertThat(fileStatus(url)).isEqualTo(404);
    }
}
