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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link TicketTemplateController}'s own behavior — mirrors
 * {@code TicketTypeControllerTest}. Security-filter enforcement (the
 * cross-org-resolved-from-persisted-eventId rule) is exercised separately in
 * the full-stack {@code TicketTemplateAccessIntegrationTest}.
 */
@WebMvcTest(TicketTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketTemplateService ticketTemplateService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private TicketTemplate template(UUID id, UUID eventId, String primaryColor) {
        return TicketTemplate.builder()
                .id(id)
                .eventId(eventId)
                .ticketTypeId(null)
                .format(TicketTemplateFormat.DIGITAL)
                .logoUrl("https://example.com/logo.png")
                .backgroundImageUrl("https://example.com/bg.png")
                .primaryColor(primaryColor)
                .build();
    }

    @Test
    void update_validRequest_returns200() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.update(eq(templateId), any())).thenReturn(template(templateId, eventId, "#000000"));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.primaryColor").value("#000000"));
    }

    @Test
    void update_unknownTemplate_returns404() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateService.update(eq(templateId), any()))
                .thenThrow(new ResourceNotFoundException("Ticket template " + templateId + " not found"));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthorizedCaller_returns403() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateService.update(eq(templateId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates"));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_invalidBackgroundImageUrl_returns400() throws Exception {
        UUID templateId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"backgroundImageUrl":"not-a-url"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
