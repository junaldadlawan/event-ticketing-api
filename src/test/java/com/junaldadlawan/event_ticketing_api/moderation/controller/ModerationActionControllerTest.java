package com.junaldadlawan.event_ticketing_api.moderation.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionResponse;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationActionType;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
import com.junaldadlawan.event_ticketing_api.moderation.service.ModerationActionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link ModerationActionController}'s own behavior - mirrors
 * {@code DisputeControllerTest}. Real admin-only enforcement (SecurityConfig's
 * {@code hasRole("ADMIN")} matcher plus the service's own {@code
 * requireAdmin()}) is exercised in the integration test.
 */
@WebMvcTest(ModerationActionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ModerationActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModerationActionService moderationActionService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private ModerationActionResponse response(UUID id, ModerationTargetType targetType, ModerationActionType action) {
        return new ModerationActionResponse(id, targetType, UUID.randomUUID(), action, "reason",
                "APPROVED", UUID.randomUUID(), Instant.now());
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(moderationActionService.create(any())).thenReturn(response(UUID.randomUUID(), ModerationTargetType.ORGANIZATION, ModerationActionType.SUSPEND));

        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .contentType("application/json")
                        .content("{\"targetType\":\"ORGANIZATION\",\"targetId\":\"" + targetId + "\",\"action\":\"SUSPEND\",\"reason\":\"fraud\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action").value("SUSPEND"));
    }

    @Test
    void create_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + UUID.randomUUID() + "\",\"action\":\"SUSPEND\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingTargetType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .contentType("application/json")
                        .content("{\"targetId\":\"" + UUID.randomUUID() + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_targetAlreadySuspended_returns409() throws Exception {
        when(moderationActionService.create(any())).thenThrow(new ConflictException("This organization is already suspended"));

        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .contentType("application/json")
                        .content("{\"targetType\":\"ORGANIZATION\",\"targetId\":\"" + UUID.randomUUID() + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_unknownTarget_returns404() throws Exception {
        when(moderationActionService.create(any())).thenThrow(new ResourceNotFoundException("Organization not found"));

        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .contentType("application/json")
                        .content("{\"targetType\":\"ORGANIZATION\",\"targetId\":\"" + UUID.randomUUID() + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_nonAdmin_returns403() throws Exception {
        when(moderationActionService.create(any())).thenThrow(new ForbiddenException("Admin access required"));

        mockMvc.perform(post("/api/v1/admin/moderation-actions")
                        .contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + UUID.randomUUID() + "\",\"action\":\"SUSPEND\",\"reason\":\"r\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns200_withPageResponseShape() throws Exception {
        Page<ModerationActionResponse> page = new PageImpl<>(List.of(response(UUID.randomUUID(), ModerationTargetType.USER, ModerationActionType.SUSPEND)));
        when(moderationActionService.list(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/moderation-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }
}
