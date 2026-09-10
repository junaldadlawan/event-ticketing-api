package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the {@code SecurityConfig} rewrite that replaced
 * {@code hasAnyRole("ORGANIZER","ADMIN")} on the event-mutation endpoints with
 * a SpEL expression backed by {@code OrganizationAccessGuard.isOwnerOrOrganizerAnywhere}.
 * Confirms the rewrite didn't silently lock out event creation, and that a
 * plain CUSTOMER (no org membership) still can't create events — mirrors
 * {@code UserSecurityIntegrationTest}'s real-JWT-through-real-SecurityConfig
 * style. Only {@code POST /api/v1/events} is exercised directly (PUT/DELETE
 * share the exact same {@code .access(...)} matcher expression, so the same
 * authorization outcome applies without needing a persisted event per verb).
 * <p>
 * Known accepted limitation carried over from the plan: this can only prove
 * "owner/organizer of *some* organization can create events," not "of *this
 * event's* organization" — {@code Event.organizationId} is still an unwired
 * placeholder (Phase 3).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventOrganizationAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID eventId : createdEventIds) {
            eventRepository.deleteById(eventId);
        }
        createdEventIds.clear();
    }

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .role(role)
                .build();
    }

    private String validEventRequestBody() {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        return """
                {"title":"Org Access Regression Event","description":"desc","category":"music",
                "venue":1,"startAt":"%s","endAt":"%s","timezone":"UTC","ticketPrefix":"ABC"}
                """.formatted(startAt, endAt);
    }

    private void grantOrgRole(UUID userId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder()
                .userId(userId)
                .organizationId(UUID.randomUUID())
                .build();
        member.getRoles().add(role);
        organizationMemberRepository.save(member);
        createdMembers.add(member);
    }

    private void trackCreatedEvent(MvcResult result) throws Exception {
        UUID eventId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdEventIds.add(eventId);
    }

    @Test
    void adminToken_canCreateEvent() throws Exception {
        String token = jwtService.generateAccessToken(inMemoryUser(Role.ADMIN));

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validEventRequestBody()))
                .andExpect(status().isCreated())
                .andReturn();
        trackCreatedEvent(result);
    }

    @Test
    void ownerOfSomeOrganization_canCreateEvent() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        grantOrgRole(owner.getId(), OrganizationRole.OWNER);
        String token = jwtService.generateAccessToken(owner);

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validEventRequestBody()))
                .andExpect(status().isCreated())
                .andReturn();
        trackCreatedEvent(result);
    }

    @Test
    void organizerOfSomeOrganization_canCreateEvent() throws Exception {
        User organizer = inMemoryUser(Role.CUSTOMER);
        grantOrgRole(organizer.getId(), OrganizationRole.ORGANIZER);
        String token = jwtService.generateAccessToken(organizer);

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validEventRequestBody()))
                .andExpect(status().isCreated())
                .andReturn();
        trackCreatedEvent(result);
    }

    @Test
    void checkInStaffOnly_cannotCreateEvent() throws Exception {
        User staff = inMemoryUser(Role.CUSTOMER);
        grantOrgRole(staff.getId(), OrganizationRole.CHECK_IN_STAFF);
        String token = jwtService.generateAccessToken(staff);

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validEventRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void plainCustomerToken_cannotCreateEvent() throws Exception {
        String token = jwtService.generateAccessToken(inMemoryUser(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validEventRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_cannotCreateEvent() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(validEventRequestBody()))
                .andExpect(status().isUnauthorized());
    }
}
