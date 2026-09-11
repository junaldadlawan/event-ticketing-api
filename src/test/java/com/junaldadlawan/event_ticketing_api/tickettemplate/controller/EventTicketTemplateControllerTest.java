package com.junaldadlawan.event_ticketing_api.tickettemplate.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.service.TicketTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventTicketTemplateController}'s own behavior
 * (request validation, response mapping, status codes) — mirrors
 * {@code EventTicketTypeControllerTest}. Security-filter enforcement (401/403,
 * cross-org rejection, the always-authenticated list rule) is exercised
 * separately in the full-stack {@code TicketTemplateAccessIntegrationTest}.
 */
@WebMvcTest(EventTicketTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventTicketTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketTemplateService ticketTemplateService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private TicketTemplate template(UUID id, UUID eventId) {
        return TicketTemplate.builder()
                .id(id)
                .eventId(eventId)
                .ticketTypeId(null)
                .format(TicketTemplateFormat.DIGITAL)
                .logoUrl("https://example.com/logo.png")
                .backgroundImageUrl("https://example.com/bg.png")
                .primaryColor("#ABCDEF")
                .build();
    }

    private String validCreateBody() {
        return """
                {"format":"DIGITAL","logoUrl":"https://example.com/logo.png",
                "backgroundImageUrl":"https://example.com/bg.png","primaryColor":"#ABCDEF"}
                """;
    }

    // ---- create() ----

    @Test
    void create_validRequest_returns201_withBrandingFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(template(templateId, eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.format").value("DIGITAL"))
                .andExpect(jsonPath("$.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.backgroundImageUrl").value("https://example.com/bg.png"))
                .andExpect(jsonPath("$.primaryColor").value("#ABCDEF"));
    }

    @Test
    void create_missingFormat_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = """
                {"logoUrl":"https://example.com/logo.png"}
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidLogoUrl_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = """
                {"format":"DIGITAL","logoUrl":"not-a-url"}
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates"));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
    }

    // ---- list() ----

    @Test
    void list_existingEvent_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.list(eventId)).thenReturn(List.of(template(UUID.randomUUID(), eventId)));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()));
    }

    @Test
    void list_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.list(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.list(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates"));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId))
                .andExpect(status().isForbidden());
    }
}
