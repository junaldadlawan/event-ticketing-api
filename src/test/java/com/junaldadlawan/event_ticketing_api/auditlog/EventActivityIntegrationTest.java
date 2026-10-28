package com.junaldadlawan.event_ticketing_api.auditlog;

import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
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
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-EVENT-006 end to end: what an organizer does to an event shows in its activity history, to them only. */
@SpringBootTest
@AutoConfigureMockMvc
class EventActivityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private AuditLogEntryRepository auditLogEntryRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> promoCodeIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        eventIds.forEach(id -> auditLogEntryRepository.deleteAll(
                auditLogEntryRepository.findByEventIdOrderByCreatedAtDesc(id, Pageable.unpaged()).getContent()));
        promoCodeIds.forEach(id -> promoCodeRepository.findById(id).ifPresent(promoCodeRepository::delete));
        ticketTypeIds.forEach(id -> ticketTypeRepository.findById(id).ifPresent(ticketTypeRepository::delete));
        eventIds.forEach(id -> eventRepository.findById(id).ifPresent(eventRepository::delete));
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(id -> organizationRepository.findById(id).ifPresent(organizationRepository::delete));
    }

    @Test
    void whatTheOrganizerDoes_showsInTheEventsHistory_newestFirst_forThemOnly() throws Exception {
        User owner = inMemoryUser();
        String ownerToken = jwtService.generateAccessToken(owner);
        String strangerToken = jwtService.generateAccessToken(inMemoryUser());
        UUID orgId = persistOrganization(owner.getId());
        grantOwner(owner.getId(), orgId);
        UUID eventId = persistEvent(orgId);

        UUID ticketTypeId = createdId(ticketTypeIds, post("/api/v1/events/{eventId}/ticket-types", eventId)
                .header("Authorization", "Bearer " + ownerToken).contentType("application/json").content(TICKET_TYPE_BODY));
        mockMvc.perform(patch("/api/v1/ticket-types/{id}", ticketTypeId).header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"name\":\"VIP\"}")).andExpect(status().isOk());
        createdId(promoCodeIds, post("/api/v1/events/{eventId}/promo-codes", eventId)
                .header("Authorization", "Bearer " + ownerToken).contentType("application/json").content(promoCodeBody()));

        mockMvc.perform(get("/api/v1/events/{eventId}/activity", eventId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].action").value("promo_code.created"))
                .andExpect(jsonPath("$.content[1].action").value("ticket_type.updated"))
                .andExpect(jsonPath("$.content[2].action").value("ticket_type.created"))
                .andExpect(jsonPath("$.content[2].targetId").value(ticketTypeId.toString()))
                .andExpect(jsonPath("$.content[0].actorId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.content[0].eventId").value(eventId.toString()));

        mockMvc.perform(get("/api/v1/events/{eventId}/activity", eventId).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{eventId}/activity", eventId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events/{eventId}/activity", UUID.randomUUID()).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUpdateLogsExactlyWhatChanged_aNoOpUpdateLogsNothing_andADeletedTicketTypeKeepsItsName() throws Exception {
        User owner = inMemoryUser();
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID orgId = persistOrganization(owner.getId());
        grantOwner(owner.getId(), orgId);
        List<String> categories = new ArrayList<>();
        objectMapper.readTree(mockMvc.perform(get("/api/v1/categories")).andReturn().getResponse().getContentAsString())
                .forEach(category -> categories.add(category.get("name").asText()));
        UUID eventId = persistEvent(orgId);
        Event event = eventRepository.findById(eventId).orElseThrow();
        event.setCategory(categories.get(0));
        eventRepository.save(event);

        // title + category change, description sent unchanged
        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId).header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"title\":\"Event Activity Test Event 2026\",\"category\":\"" + categories.get(1) + "\",\"description\":\"desc\"}"))
                .andExpect(status().isOk());
        // the same values again: nothing changes, nothing is logged
        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId).header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"title\":\"Event Activity Test Event 2026\",\"category\":\"" + categories.get(1) + "\"}"))
                .andExpect(status().isOk());
        UUID ticketTypeId = createdId(ticketTypeIds, post("/api/v1/events/{eventId}/ticket-types", eventId)
                .header("Authorization", "Bearer " + ownerToken).contentType("application/json").content(TICKET_TYPE_BODY));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/ticket-types/{id}", ticketTypeId)
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/events/{eventId}/activity", eventId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].action").value("ticket_type.deleted"))
                .andExpect(jsonPath("$.content[0].details.name").value("General Admission"))
                .andExpect(jsonPath("$.content[1].action").value("ticket_type.created"))
                .andExpect(jsonPath("$.content[1].details.name").value("General Admission"))
                .andExpect(jsonPath("$.content[2].action").value("event.updated"))
                .andExpect(jsonPath("$.content[2].details.changes.length()").value(2))
                .andExpect(jsonPath("$.content[2].details.changes[0].field").value("title"))
                .andExpect(jsonPath("$.content[2].details.changes[0].from").value("Event Activity Test Event"))
                .andExpect(jsonPath("$.content[2].details.changes[0].to").value("Event Activity Test Event 2026"))
                .andExpect(jsonPath("$.content[2].details.changes[1].field").value("category"))
                .andExpect(jsonPath("$.content[2].details.changes[1].from").value(categories.get(0)))
                .andExpect(jsonPath("$.content[2].details.changes[1].to").value(categories.get(1)));
    }

    // ---- helpers ----

    private UUID createdId(List<UUID> tracker, org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        tracker.add(id);
        return id;
    }

    private User inMemoryUser() {
        return User.builder().id(UUID.randomUUID()).email("activity-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization saved = organizationRepository.save(Organization.builder()
                .name("Event Activity Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOwner(UUID userId, UUID organizationId) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        members.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event saved = eventRepository.save(Event.builder()
                .organizationId(organizationId)
                .title("Event Activity Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.DRAFT)
                .ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build());
        eventIds.add(saved.getId());
        return saved.getId();
    }

    private static final String TICKET_TYPE_BODY = """
            {"name":"General Admission","kind":"GENERAL_ADMISSION",
            "price":{"amount":1000,"currency":"USD"},"quantityTotal":100,
            "saleStartAt":"%s","saleEndAt":"%s"}
            """.formatted(Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(5, ChronoUnit.DAYS));

    private static String promoCodeBody() {
        return """
                {"code":"ACT%s","discountType":"PERCENTAGE","discountValue":10,"validFrom":"%s","validUntil":"%s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS));
    }
}
