package com.junaldadlawan.event_ticketing_api.seatmap;

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
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring end to end
 * for the seat-map module — mirrors {@code TicketTypeAccessIntegrationTest}.
 * No creation endpoint exists in this phase's scope, so {@link SeatMap}/
 * {@link Seat} rows are inserted directly via the repositories in test setup.
 * Covers: 404 with "Event has no seat map" when none exists, the same
 * draft-visibility gating as ticket types, and the populated case returning
 * seats with their status once a seat map exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeatMapAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatMapRepository seatMapRepository;

    @Autowired
    private SeatRepository seatRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdSeatMapIds = new ArrayList<>();
    private final List<UUID> createdSeatIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID seatId : createdSeatIds) {
            seatRepository.deleteById(seatId);
        }
        createdSeatIds.clear();
        for (UUID seatMapId : createdSeatMapIds) {
            seatMapRepository.deleteById(seatMapId);
        }
        createdSeatMapIds.clear();
        for (UUID eventId : createdEventIds) {
            eventRepository.deleteById(eventId);
        }
        createdEventIds.clear();
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID orgId : createdOrgIds) {
            organizationRepository.deleteById(orgId);
        }
        createdOrgIds.clear();
    }

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .role(role)
                .build();
    }

    private UUID persistOrganization(UUID ownerId, OrganizationStatus status) {
        Organization organization = Organization.builder()
                .name("Seat Map Access Test Org " + UUID.randomUUID())
                .status(status)
                .ownerId(ownerId)
                .documents(List.of())
                .build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder()
                .userId(userId)
                .organizationId(organizationId)
                .build();
        member.getRoles().add(role);
        OrganizationMember saved = organizationMemberRepository.save(member);
        createdMembers.add(saved);
    }

    private UUID persistEvent(UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Seat Map Access Test Event")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("S" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistSeatMapWithSeats(UUID eventId) {
        SeatMap seatMap = SeatMap.builder().eventId(eventId).build();
        SeatMap savedSeatMap = seatMapRepository.save(seatMap);
        createdSeatMapIds.add(savedSeatMap.getId());

        Seat available = Seat.builder().seatMapId(savedSeatMap.getId()).section("A").row("1").seatNumber("1").status(SeatStatus.AVAILABLE).build();
        Seat held = Seat.builder().seatMapId(savedSeatMap.getId()).section("A").row("1").seatNumber("2").status(SeatStatus.HELD).build();
        Seat sold = Seat.builder().seatMapId(savedSeatMap.getId()).section("A").row("1").seatNumber("3").status(SeatStatus.SOLD).build();
        for (Seat seat : List.of(available, held, sold)) {
            createdSeatIds.add(seatRepository.save(seat).getId());
        }
        return savedSeatMap.getId();
    }

    @Test
    void get_noSeatMap_returns404WithExpectedMessage() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);

        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event has no seat map"));
    }

    @Test
    void get_seatMapExists_returns200WithSeatsAndStatus() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID seatMapId = persistSeatMapWithSeats(eventId);

        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seatMapId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.seats.length()").value(3))
                .andExpect(jsonPath("$.seats[?(@.status=='AVAILABLE')]").exists())
                .andExpect(jsonPath("$.seats[?(@.status=='HELD')]").exists())
                .andExpect(jsonPath("$.seats[?(@.status=='SOLD')]").exists());
    }

    /** Same draft-visibility gating as the ticket-type module. */
    @Test
    void get_draftEvent_gatedTheSameWayAsTicketTypes() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);

        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);

        String ownerToken = jwtService.generateAccessToken(owner);
        String strangerToken = jwtService.generateAccessToken(stranger);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);
        String adminToken = jwtService.generateAccessToken(admin);

        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        persistSeatMapWithSeats(eventId);

        // Anonymous / stranger / cross-org-owner -> 403 while the event is DRAFT.
        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());

        // Owning org's owner / admin -> 200.
        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void get_publishedEvent_anonymousSucceedsWithNoAuthorizationHeader() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        persistSeatMapWithSeats(eventId);

        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(3));
    }

    @Test
    void get_nonExistentEvent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
