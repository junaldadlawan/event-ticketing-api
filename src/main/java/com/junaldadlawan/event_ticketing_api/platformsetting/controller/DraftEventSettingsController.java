package com.junaldadlawan.event_ticketing_api.platformsetting.controller;

import com.junaldadlawan.event_ticketing_api.platformsetting.dto.DraftEventSettingsRequest;
import com.junaldadlawan.event_ticketing_api.platformsetting.dto.DraftEventSettingsResponse;
import com.junaldadlawan.event_ticketing_api.platformsetting.service.DraftEventSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BR-EVENT-007. Both fall under SecurityConfig's anyRequest().authenticated(); the service checks admin for PUT. */
@RestController
@RequestMapping("/api/v1/platform-settings/draft-events")
@RequiredArgsConstructor
public class DraftEventSettingsController {

    private final DraftEventSettingsService service;

    @GetMapping
    public DraftEventSettingsResponse get() {
        return service.get();
    }

    @PutMapping
    public DraftEventSettingsResponse update(@Valid @RequestBody DraftEventSettingsRequest request) {
        return service.update(request);
    }
}
