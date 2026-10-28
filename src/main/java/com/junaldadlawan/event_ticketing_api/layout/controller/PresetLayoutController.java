package com.junaldadlawan.event_ticketing_api.layout.controller;

import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutRequest;
import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutResponse;
import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutSummary;
import com.junaldadlawan.event_ticketing_api.layout.service.PresetLayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Preset layouts: read by owners and organizers, managed by admins. */
@RestController
@RequiredArgsConstructor
public class PresetLayoutController {

    private final PresetLayoutService presetLayoutService;

    @GetMapping("/api/v1/layout-presets")
    public List<PresetLayoutSummary> list() {
        return presetLayoutService.list();
    }

    @GetMapping("/api/v1/layout-presets/{presetId}")
    public PresetLayoutResponse get(@PathVariable UUID presetId) {
        return presetLayoutService.get(presetId);
    }

    @PostMapping("/api/v1/layout-presets")
    @ResponseStatus(HttpStatus.CREATED)
    public PresetLayoutResponse create(@Valid @RequestBody PresetLayoutRequest request) {
        return presetLayoutService.create(request);
    }

    @PutMapping("/api/v1/layout-presets/{presetId}")
    public PresetLayoutResponse replace(@PathVariable UUID presetId, @Valid @RequestBody PresetLayoutRequest request) {
        return presetLayoutService.replace(presetId, request);
    }

    @DeleteMapping("/api/v1/layout-presets/{presetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID presetId) {
        presetLayoutService.delete(presetId);
    }
}
