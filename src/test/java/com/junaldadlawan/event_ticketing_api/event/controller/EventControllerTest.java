package com.junaldadlawan.event_ticketing_api.event.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.service.EventService;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventController}'s own behavior (request validation,
 * response mapping, status codes) — mirrors {@code VenueControllerTest}.
 * Security-filter enforcement (401/403, cross-org rejection, public-draft
 * gating) is exercised separately in
 * {@code EventOrganizationAccessIntegrationTest}, since
 * {@code @WebMvcTest(addFilters = false)} never engages the real filter
 * chain and the service is fully mocked here.
 */
@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private Event event(UUID eventId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(eventId)
                .organizationId(UUID.randomUUID())
                .title("Concert Night")
                .description("A great concert")
                .category("music")
                .status(status)
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private Venue venue(UUID venueId) {
        return Venue.builder()
                .id(venueId)
                .organizationId(UUID.randomUUID())
                .name("Main Hall")
                .address("123 Main St")
                .build();
    }

    private String validCreateBody(UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        return """
                {"organizationId":"%s","title":"Concert Night","description":"A great concert",
                "category":"music","venueId":null,"startAt":"%s","endAt":"%s","timezone":"UTC"}
                """.formatted(organizationId, startAt, endAt);
    }

    // ---- create() ----

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event created = event(eventId, EventStatus.DRAFT);
        when(eventService.createEvent(any())).thenReturn(created);
        when(eventService.resolveVenue(null)).thenReturn(null);

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(validCreateBody(organizationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.ticketPrefix").value("ABC"));
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        // title omitted entirely -> @NotBlank violation -> 400, not 401/500
        // (confirming the GlobalExceptionHandler fix from an earlier phase still holds).
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        String body = """
                {"organizationId":"%s","description":"desc","category":"music",
                "startAt":"%s","endAt":"%s","timezone":"UTC"}
                """.formatted(UUID.randomUUID(), startAt, endAt);

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankTitle_returns400() throws Exception {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        String body = """
                {"organizationId":"%s","title":"   ","description":"desc","category":"music",
                "startAt":"%s","endAt":"%s","timezone":"UTC"}
                """.formatted(UUID.randomUUID(), startAt, endAt);

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingOrganizationId_returns400() throws Exception {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        String body = """
                {"title":"Concert Night","description":"desc","category":"music",
                "startAt":"%s","endAt":"%s","timezone":"UTC"}
                """.formatted(startAt, endAt);

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_startAtNotInFuture_returns400() throws Exception {
        Instant startAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endAt = Instant.now().plus(1, ChronoUnit.DAYS);
        String body = """
                {"organizationId":"%s","title":"Concert Night","description":"desc","category":"music",
                "startAt":"%s","endAt":"%s","timezone":"UTC"}
                """.formatted(UUID.randomUUID(), startAt, endAt);

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_organizationNotFound_returns404() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(eventService.createEvent(any()))
                .thenThrow(new ResourceNotFoundException("Organization " + organizationId + " not found"));

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(validCreateBody(organizationId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_organizationNotApproved_returns403() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(eventService.createEvent(any()))
                .thenThrow(new ForbiddenException("Organization is not approved"));

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(validCreateBody(organizationId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_venueFromDifferentOrganization_returns400() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(eventService.createEvent(any()))
                .thenThrow(new BadRequestException("Venue does not belong to the specified organization"));

        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(validCreateBody(organizationId)))
                .andExpect(status().isBadRequest());
    }

    // ---- get() ----

    @Test
    void get_existingEvent_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Event existing = event(eventId, EventStatus.PUBLISHED);
        existing.setVenueId(venueId);
        when(eventService.getEvent(eventId)).thenReturn(existing);
        when(eventService.resolveVenue(venueId)).thenReturn(venue(venueId));

        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Concert Night"))
                .andExpect(jsonPath("$.venue.id").value(venueId.toString()));
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEvent(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_draftEvent_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEvent(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event"));

        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isForbidden());
    }

    // ---- update() ----

    @Test
    void update_validRequest_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        Event updated = event(eventId, EventStatus.DRAFT);
        updated.setTitle("Renamed Event");
        when(eventService.updateEvent(eq(eventId), any())).thenReturn(updated);
        when(eventService.resolveVenue(null)).thenReturn(null);

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .contentType("application/json")
                        .content("""
                                {"title":"Renamed Event"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed Event"));
    }

    @Test
    void update_blankTitle_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.updateEvent(eq(eventId), any()))
                .thenThrow(new BadRequestException("Event title must not be blank"));

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .contentType("application/json")
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_invalidImageUrl_returns400() throws Exception {
        // @URL constraint violation on images -> 400 via bean validation, not 401/500.
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .contentType("application/json")
                        .content("""
                                {"images":["not-a-url"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_crossOrgCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.updateEvent(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event"));

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .contentType("application/json")
                        .content("""
                                {"title":"Hijacked"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.updateEvent(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .contentType("application/json")
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ---- publish() ----

    @Test
    void publish_validRequest_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        Event published = event(eventId, EventStatus.PUBLISHED);
        when(eventService.publishEvent(eventId)).thenReturn(published);
        when(eventService.resolveVenue(null)).thenReturn(null);

        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void publish_alreadyPublished_returns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.publishEvent(eventId)).thenThrow(new ConflictException("Event is not in a publishable state"));

        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId))
                .andExpect(status().isConflict());
    }

    @Test
    void publish_crossOrgCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.publishEvent(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event"));

        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId))
                .andExpect(status().isForbidden());
    }

    // ---- cancel() ----

    @Test
    void cancel_validRequest_returns202() throws Exception {
        UUID eventId = UUID.randomUUID();
        Event cancelled = event(eventId, EventStatus.CANCELLED);
        when(eventService.cancelEvent(eventId)).thenReturn(cancelled);
        when(eventService.resolveVenue(null)).thenReturn(null);

        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_alreadyCancelled_returns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.cancelEvent(eventId)).thenThrow(new ConflictException("Event is not in a cancellable state"));

        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_crossOrgCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.cancelEvent(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event"));

        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId))
                .andExpect(status().isForbidden());
    }

    // ---- deleteEvent() ----

    @Test
    void delete_validRequest_returns204() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_crossOrgCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event"))
                .when(eventService).delete(eventId);

        mockMvc.perform(delete("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Event " + eventId + " not found"))
                .when(eventService).delete(eventId);

        mockMvc.perform(delete("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isNotFound());
    }
}
