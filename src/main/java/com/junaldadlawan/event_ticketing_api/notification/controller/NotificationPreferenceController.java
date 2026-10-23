package com.junaldadlawan.event_ticketing_api.notification.controller;

import com.junaldadlawan.event_ticketing_api.notification.dto.NotificationPreferencesDto;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BR-NOTIFY-002: the caller's own notification on/off switches. */
@RestController
@RequestMapping("/api/v1/users/me/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping
    public NotificationPreferencesDto get() {
        return notificationPreferenceService.getMine();
    }

    @PutMapping
    public NotificationPreferencesDto update(@Valid @RequestBody NotificationPreferencesDto request) {
        return notificationPreferenceService.updateMine(request);
    }
}
