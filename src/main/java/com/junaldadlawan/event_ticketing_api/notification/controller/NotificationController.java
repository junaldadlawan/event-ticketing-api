package com.junaldadlawan.event_ticketing_api.notification.controller;

import com.junaldadlawan.event_ticketing_api.notification.dto.NotificationResponse;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list() {
        return notificationService.listMyNotifications();
    }
}
