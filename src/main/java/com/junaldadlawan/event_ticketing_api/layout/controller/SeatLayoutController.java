package com.junaldadlawan.event_ticketing_api.layout.controller;

import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutRequest;
import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutResponse;
import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutSummary;
import com.junaldadlawan.event_ticketing_api.layout.service.SeatLayoutService;
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

/** An organization's seat layouts: list, create and copy through the organization, the rest by the layout's own id. */
@RestController
@RequiredArgsConstructor
public class SeatLayoutController {

    private final SeatLayoutService seatLayoutService;

    @GetMapping("/api/v1/organizations/{orgId}/layouts")
    public List<SeatLayoutSummary> list(@PathVariable UUID orgId) {
        return seatLayoutService.list(orgId);
    }

    @PostMapping("/api/v1/organizations/{orgId}/layouts")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatLayoutResponse create(@PathVariable UUID orgId, @Valid @RequestBody SeatLayoutRequest request) {
        return seatLayoutService.create(orgId, request);
    }

    @PostMapping("/api/v1/organizations/{orgId}/layouts/from-preset/{presetId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatLayoutResponse copyPreset(@PathVariable UUID orgId, @PathVariable UUID presetId) {
        return seatLayoutService.copyPreset(orgId, presetId);
    }

    @GetMapping("/api/v1/layouts/{layoutId}")
    public SeatLayoutResponse get(@PathVariable UUID layoutId) {
        return seatLayoutService.get(layoutId);
    }

    @PutMapping("/api/v1/layouts/{layoutId}")
    public SeatLayoutResponse replace(@PathVariable UUID layoutId, @Valid @RequestBody SeatLayoutRequest request) {
        return seatLayoutService.replace(layoutId, request);
    }

    @DeleteMapping("/api/v1/layouts/{layoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID layoutId) {
        seatLayoutService.delete(layoutId);
    }
}
