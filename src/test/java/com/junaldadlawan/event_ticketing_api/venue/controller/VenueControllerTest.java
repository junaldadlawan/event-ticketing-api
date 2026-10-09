package com.junaldadlawan.event_ticketing_api.venue.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.service.VenueService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link VenueController}'s own behavior (request validation,
 * response mapping, status codes) — mirrors {@code OrganizationControllerTest}.
 * Security-filter enforcement (the public GET, 401/403 on PATCH) is exercised
 * separately in {@code VenueSecurityIntegrationTest}, since
 * {@code @WebMvcTest(addFilters = false)} never engages the real filter chain.
 */
@WebMvcTest(VenueController.class)
@AutoConfigureMockMvc(addFilters = false)
class VenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private Venue venue(UUID venueId) {
        return Venue.builder()
                .id(venueId)
                .organizationId(UUID.randomUUID())
                .name("Main Hall")
                .address("123 Main St")
                .latitude(1.1)
                .longitude(2.2)
                .build();
    }

    @Test
    void get_existingVenue_returns200() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(venueService.get(venueId)).thenReturn(venue(venueId));

        mockMvc.perform(get("/api/v1/venues/{venueId}", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Hall"));
    }

    @Test
    void get_unknownVenue_returns404() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(venueService.get(venueId)).thenThrow(new ResourceNotFoundException("Venue " + venueId + " not found"));

        mockMvc.perform(get("/api/v1/venues/{venueId}", venueId))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_validRequest_returns200() throws Exception {
        UUID venueId = UUID.randomUUID();
        Venue updated = venue(venueId);
        updated.setName("New Name");
        when(venueService.update(eq(venueId), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void update_blankName_returns400() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(venueService.update(eq(venueId), any()))
                .thenThrow(new BadRequestException("Venue name must not be blank"));

        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .contentType("application/json")
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_forbidden_returns403() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(venueService.update(eq(venueId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner or organizer may manage its venues"));

        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unknownVenue_returns404() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(venueService.update(eq(venueId), any()))
                .thenThrow(new ResourceNotFoundException("Venue " + venueId + " not found"));

        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isNotFound());
    }

    private Venue venueInOrg(UUID orgId) {
        return Venue.builder()
                .id(UUID.randomUUID())
                .organizationId(orgId)
                .name("Main Hall")
                .address("123 Main St")
                .latitude(1.1)
                .longitude(2.2)
                .build();
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID orgId = UUID.randomUUID();
        Venue saved = venueInOrg(orgId);
        when(venueService.create(eq(orgId), any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Main Hall","address":"123 Main St","latitude":1.1,"longitude":2.2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main Hall"))
                .andExpect(jsonPath("$.organizationId").value(orgId.toString()));
    }

    @Test
    void create_missingName_returns400() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .contentType("application/json")
                        .content("""
                                {"address":"123 Main St"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .contentType("application/json")
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_forbidden_returns403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(venueService.create(eq(orgId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner or organizer may manage its venues"));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Main Hall"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nonExistentOrganization_returns404() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(venueService.create(eq(orgId), any()))
                .thenThrow(new ResourceNotFoundException("Organization " + orgId + " not found"));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Main Hall"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_returnsMappedList() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(venueService.list(orgId)).thenReturn(List.of(venueInOrg(orgId)));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/venues", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Main Hall"));
    }

    @Test
    void list_forbidden_returns403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(venueService.list(orgId)).thenThrow(new ForbiddenException("Not authorized to view this organization's venues"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/venues", orgId))
                .andExpect(status().isForbidden());
    }
}
