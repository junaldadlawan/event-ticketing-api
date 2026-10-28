package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventCompletionService;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-EVENT-005 end to end: an ended event is completed on read and by the sweep, every organizer write to it
 * answers 409 "Event has ended", and buying stops - while the reads keep working.
 * <p>
 * Only touches the events it creates: the sweep's own query covers the whole table, so it is exercised through
 * {@link EventCompletionService#completeEnded} with this test's ids (the background sweep is off in the test run).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventCompletionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EventCompletionService eventCompletionService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private TicketTemplateRepository ticketTemplateRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CheckoutIdempotencyKeyRepository idempotencyKeyRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> promoCodeIds = new ArrayList<>();
    private final List<UUID> templateIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> cartItemIds = new ArrayList<>();
    private final List<UUID> idempotencyKeys = new ArrayList<>();

    @AfterEach
    void tearDown() {
        idempotencyKeys.forEach(id -> idempotencyKeyRepository.findById(id).ifPresent(idempotencyKeyRepository::delete));
        cartItemIds.forEach(id -> cartItemRepository.findById(id).ifPresent(cartItemRepository::delete));
        cartIds.forEach(id -> cartRepository.findById(id).ifPresent(cartRepository::delete));
        templateIds.forEach(id -> ticketTemplateRepository.findById(id).ifPresent(ticketTemplateRepository::delete));
        promoCodeIds.forEach(id -> promoCodeRepository.findById(id).ifPresent(promoCodeRepository::delete));
        ticketTypeIds.forEach(id -> ticketTypeRepository.findById(id).ifPresent(ticketTypeRepository::delete));
        eventIds.forEach(id -> eventRepository.findById(id).ifPresent(eventRepository::delete));
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(id -> organizationRepository.findById(id).ifPresent(organizationRepository::delete));
    }

    // ---- auto-completion ----

    @Test
    void readingAnEndedEvent_completesIt() throws Exception {
        UUID eventId = persistEvent(persistOrganization(UUID.randomUUID()), EventStatus.PUBLISHED, ended());

        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(statusOf(eventId)).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    void listingTicketTypesOfAnEndedEvent_completesIt() throws Exception {
        UUID eventId = persistEvent(persistOrganization(UUID.randomUUID()), EventStatus.ON_SALE, ended());

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId)).andExpect(status().isOk());

        assertThat(statusOf(eventId)).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    void publicSearch_neverListsAnEndedEventAsLive() throws Exception {
        String title = "Completion Search " + UUID.randomUUID();
        UUID eventId = persistEvent(persistOrganization(UUID.randomUUID()), EventStatus.PUBLISHED, ended(), title);

        mockMvc.perform(get("/api/v1/events").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        assertThat(statusOf(eventId)).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    void theSweep_completesOnlyLiveEndedEvents_once() {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID published = persistEvent(orgId, EventStatus.PUBLISHED, ended());
        UUID soldOut = persistEvent(orgId, EventStatus.SOLD_OUT, ended());
        UUID cancelled = persistEvent(orgId, EventStatus.CANCELLED, ended());
        UUID draft = persistEvent(orgId, EventStatus.DRAFT, ended());
        UUID upcoming = persistEvent(orgId, EventStatus.PUBLISHED, Instant.now().plus(3, ChronoUnit.DAYS));
        List<UUID> all = List.of(published, soldOut, cancelled, draft, upcoming);

        assertThat(eventCompletionService.completeEnded(all)).isEqualTo(2);
        assertThat(eventCompletionService.completeEnded(all)).isZero();

        assertThat(statusOf(published)).isEqualTo(EventStatus.COMPLETED);
        assertThat(statusOf(soldOut)).isEqualTo(EventStatus.COMPLETED);
        assertThat(statusOf(cancelled)).isEqualTo(EventStatus.CANCELLED);
        assertThat(statusOf(draft)).isEqualTo(EventStatus.DRAFT);
        assertThat(statusOf(upcoming)).isEqualTo(EventStatus.PUBLISHED);
    }

    // ---- read-only once ended ----

    @Test
    void anEventPastItsEndTime_refusesEveryWrite_evenBeforeItIsCompleted() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED, Instant.now().plus(1, ChronoUnit.DAYS));

        // Set up while the event is still on: a ticket type whose sale window runs PAST the event's end, a promo
        // code, a template, and a cart holding a ticket.
        UUID ticketTypeId = createdId(ticketTypeIds, mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                .header("Authorization", "Bearer " + ownerToken).contentType("application/json")
                .content(ticketTypeBody())).andExpect(status().isCreated()).andReturn());
        UUID promoCodeId = createdId(promoCodeIds, mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                .header("Authorization", "Bearer " + ownerToken).contentType("application/json")
                .content(promoCodeBody("ENDED1"))).andExpect(status().isCreated()).andReturn());
        UUID templateId = createdId(templateIds, mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                .header("Authorization", "Bearer " + ownerToken).contentType("application/json")
                .content(TEMPLATE_BODY)).andExpect(status().isCreated()).andReturn());
        UUID cartId = createdId(cartIds, mockMvc.perform(post("/api/v1/carts").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isCreated()).andReturn());
        MvcResult added = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken).contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated()).andReturn();
        UUID itemId = UUID.fromString(objectMapper.readTree(added.getResponse().getContentAsString()).get("items").get(0).get("id").asText());
        cartItemIds.add(itemId);

        // The event ends; it is still PUBLISHED in the database (no sweep, no read yet).
        Event event = eventRepository.findById(eventId).orElseThrow();
        event.setStartAt(Instant.now().minus(3, ChronoUnit.HOURS));
        event.setEndAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        eventRepository.save(event);
        assertThat(statusOf(eventId)).isEqualTo(EventStatus.PUBLISHED);

        // event
        expectEnded(patch("/api/v1/events/{eventId}", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"title\":\"Too late\"}"));
        expectEnded(post("/api/v1/events/{eventId}/cancel", eventId).header("Authorization", "Bearer " + ownerToken));
        expectEnded(delete("/api/v1/events/{eventId}", eventId).header("Authorization", "Bearer " + ownerToken));
        // ticket types
        expectEnded(post("/api/v1/events/{eventId}/ticket-types", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content(ticketTypeBody()));
        expectEnded(patch("/api/v1/ticket-types/{id}", ticketTypeId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"name\":\"Too late\"}"));
        expectEnded(put("/api/v1/ticket-types/{id}/sales-status", ticketTypeId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"status\":\"PAUSED\"}"));
        expectEnded(put("/api/v1/ticket-types/{id}/sales-status", ticketTypeId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"));
        expectEnded(put("/api/v1/events/{eventId}/ticket-types/order", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"ticketTypeIds\":[\"" + ticketTypeId + "\"]}"));
        expectEnded(delete("/api/v1/ticket-types/{id}", ticketTypeId).header("Authorization", "Bearer " + ownerToken));
        // promo codes
        expectEnded(post("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content(promoCodeBody("ENDED2")));
        expectEnded(patch("/api/v1/promo-codes/{id}", promoCodeId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"discountValue\":20}"));
        expectEnded(put("/api/v1/promo-codes/{id}/status", promoCodeId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"status\":\"PAUSED\"}"));
        expectEnded(delete("/api/v1/promo-codes/{id}", promoCodeId).header("Authorization", "Bearer " + ownerToken));
        // ticket templates
        expectEnded(post("/api/v1/events/{eventId}/ticket-templates", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content(TEMPLATE_BODY));
        expectEnded(patch("/api/v1/ticket-templates/{id}", templateId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"primaryColor\":\"#123456\"}"));
        expectEnded(delete("/api/v1/ticket-templates/{id}", templateId).header("Authorization", "Bearer " + ownerToken));
        // sales: adding more, and checking out what the cart already holds
        expectEnded(post("/api/v1/carts/{cartId}/items", cartId).header("Authorization", "Bearer " + buyerToken)
                .contentType("application/json").content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}"));
        UUID idempotencyKey = UUID.randomUUID();
        idempotencyKeys.add(idempotencyKey);
        expectEnded(post("/api/v1/carts/{cartId}/checkout", cartId).header("Authorization", "Bearer " + buyerToken)
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType("application/json").content("{\"paymentMethodToken\":\"tok_visa\"}"));

        // Nothing changed, and the reads still work.
        assertThat(eventRepository.findById(eventId).orElseThrow().getTitle()).isEqualTo("Event Completion Test Event");
        assertThat(ticketTypeRepository.findById(ticketTypeId).orElseThrow().getDeletedAt()).isNull();
        assertThat(promoCodeRepository.findById(promoCodeId).orElseThrow().isPaused()).isFalse();
        mockMvc.perform(get("/api/v1/ticket-types/{id}", ticketTypeId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());
    }

    @Test
    void aCompletedEvent_refusesWrites_andCannotBeBoughtFrom() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId);
        // COMPLETED, yet its end time is (oddly) still ahead and a ticket type is inside its sale window
        UUID eventId = persistEvent(orgId, EventStatus.COMPLETED, Instant.now().plus(1, ChronoUnit.DAYS));
        UUID ticketTypeId = persistTicketType(eventId);
        UUID cartId = createdId(cartIds, mockMvc.perform(post("/api/v1/carts").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isCreated()).andReturn());

        expectEnded(patch("/api/v1/events/{eventId}", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"title\":\"Too late\"}"));
        expectEnded(post("/api/v1/events/{eventId}/cancel", eventId).header("Authorization", "Bearer " + ownerToken));
        expectEnded(post("/api/v1/events/{eventId}/ticket-types", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content(ticketTypeBody()));
        expectEnded(post("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content(promoCodeBody("DONE1")));
        expectEnded(post("/api/v1/events/{eventId}/ticket-templates", eventId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content(TEMPLATE_BODY));
        expectEnded(post("/api/v1/carts/{cartId}/items", cartId).header("Authorization", "Bearer " + buyerToken)
                .contentType("application/json").content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}"));

        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ---- helpers ----

    private void expectEnded(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(EventCompletionService.ENDED_MESSAGE));
    }

    private static Instant ended() {
        return Instant.now().minus(1, ChronoUnit.HOURS);
    }

    private EventStatus statusOf(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow().getStatus();
    }

    private UUID createdId(List<UUID> tracker, MvcResult result) throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        tracker.add(id);
        return id;
    }

    private User inMemoryUser(Role role) {
        return User.builder().id(UUID.randomUUID()).email("completion-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization saved = organizationRepository.save(Organization.builder()
                .name("Event Completion Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        members.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId, EventStatus status, Instant endAt) {
        return persistEvent(organizationId, status, endAt, "Event Completion Test Event");
    }

    private UUID persistEvent(UUID organizationId, EventStatus status, Instant endAt, String title) {
        Event saved = eventRepository.save(Event.builder()
                .organizationId(organizationId)
                .title(title)
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(endAt.minus(2, ChronoUnit.HOURS))
                .endAt(endAt)
                .timezone("UTC")
                .build());
        eventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTicketType(UUID eventId) {
        var saved = ticketTypeRepository.save(com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType.builder()
                .eventId(eventId)
                .name("General Admission")
                .kind(com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind.GENERAL_ADMISSION)
                .price(com.junaldadlawan.event_ticketing_api.common.entity.Money.builder().amount(1000).currency("USD").build())
                .quantityTotal(10)
                .quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build());
        ticketTypeIds.add(saved.getId());
        return saved.getId();
    }

    /** On sale now, and until 20h from now: before the 1h-before-start cut-off, yet past the event's end once the test moves it. */
    private static String ticketTypeBody() {
        return """
                {"name":"General Admission","kind":"GENERAL_ADMISSION",
                "price":{"amount":1000,"currency":"USD"},"quantityTotal":100,
                "saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(20, ChronoUnit.HOURS));
    }

    private static String promoCodeBody(String code) {
        return """
                {"code":"%s","discountType":"PERCENTAGE","discountValue":10,"validFrom":"%s","validUntil":"%s"}
                """.formatted(code, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(5, ChronoUnit.DAYS));
    }

    private static final String TEMPLATE_BODY = """
            {"format":"DIGITAL","logoUrl":"https://example.com/logo.png",
            "backgroundImageUrl":"https://example.com/bg.png","primaryColor":"#ABCDEF"}
            """;
}
