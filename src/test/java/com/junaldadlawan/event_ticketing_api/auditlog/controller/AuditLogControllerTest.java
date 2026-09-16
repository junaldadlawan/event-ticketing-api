package com.junaldadlawan.event_ticketing_api.auditlog.controller;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuditLogController}'s own behavior - mirrors
 * {@code ModerationActionControllerTest}. Real admin-only enforcement
 * (SecurityConfig's {@code hasRole("ADMIN")} matcher plus the service's own
 * {@code requireAdmin()}) is exercised in the integration test.
 */
@WebMvcTest(AuditLogController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private AuditLogEntry entry(UUID actorId) {
        return AuditLogEntry.builder().id(UUID.randomUUID()).actorId(actorId).action("refund.issued")
                .targetType("Refund").targetId(UUID.randomUUID()).createdAt(Instant.now()).build();
    }

    @Test
    void list_noFilter_returns200_withPageResponseShape() throws Exception {
        Page<AuditLogEntry> page = new PageImpl<>(List.of(entry(UUID.randomUUID())));
        when(auditLogService.list(isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("refund.issued"));
    }

    @Test
    void list_withActorIdFilter_passesItThrough() throws Exception {
        UUID actorId = UUID.randomUUID();
        Page<AuditLogEntry> page = new PageImpl<>(List.of(entry(actorId)));
        when(auditLogService.list(eq(actorId), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit-log").param("actorId", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").value(actorId.toString()));
    }

    @Test
    void list_nonAdmin_returns403() throws Exception {
        when(auditLogService.list(isNull(), any())).thenThrow(new ForbiddenException("Admin access required"));

        mockMvc.perform(get("/api/v1/audit-log"))
                .andExpect(status().isForbidden());
    }
}
