package com.junaldadlawan.event_ticketing_api.notification.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.notification.dto.NotificationResponse;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationChannel;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationStatus;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link NotificationController} — mirrors {@code
 * WaitlistControllerTest}'s style (request/response shape, status-code
 * mapping). Security/RBAC enforcement (the new {@code SecurityConfig}
 * matcher for {@code GET /users/me/notifications}, and the anonymous-401
 * case) is exercised separately in {@code NotificationIntegrationTest} —
 * {@code addFilters = false} here disables the real filter chain entirely.
 */
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private NotificationResponse response(NotificationType type, NotificationStatus status) {
        return new NotificationResponse(UUID.randomUUID(), type, NotificationChannel.EMAIL, "Order",
                UUID.randomUUID(), status, status == NotificationStatus.SENT ? Instant.now() : null, Instant.now());
    }

    @Test
    void list_returns200_asPlainJsonArray_notPaginationWrapper() throws Exception {
        when(notificationService.listMyNotifications())
                .thenReturn(List.of(response(NotificationType.ORDER_CONFIRMATION, NotificationStatus.SENT)));

        mockMvc.perform(get("/api/v1/users/me/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ORDER_CONFIRMATION"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void list_noEntries_returns200_withEmptyArray() throws Exception {
        when(notificationService.listMyNotifications()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/me/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_failedNotification_reflectsStatus_withNullSentAt() throws Exception {
        when(notificationService.listMyNotifications())
                .thenReturn(List.of(response(NotificationType.REFUND_CONFIRMATION, NotificationStatus.FAILED)));

        mockMvc.perform(get("/api/v1/users/me/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].sentAt").doesNotExist());
    }
}
