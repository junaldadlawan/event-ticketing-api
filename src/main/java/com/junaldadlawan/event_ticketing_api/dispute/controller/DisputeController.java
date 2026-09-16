package com.junaldadlawan.event_ticketing_api.dispute.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeCreateRequest;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeResponse;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
import com.junaldadlawan.event_ticketing_api.dispute.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code /api/v1/disputes/**} - SecurityConfig matcher is a plain {@code
 * authenticated()} (no admin-only path restriction at the HTTP layer); real
 * authorization (raiser-or-admin visibility, admin-only list/update) lives
 * in {@link DisputeService}, same idiom as every other module in this
 * codebase.
 */
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeResponse create(@Valid @RequestBody DisputeCreateRequest request) {
        return disputeService.create(request);
    }

    @GetMapping
    public PageResponse<DisputeResponse> list(@RequestParam(required = false) DisputeStatus status,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(disputeService.list(status, pageable));
    }

    @GetMapping("/{disputeId}")
    public DisputeResponse get(@PathVariable UUID disputeId) {
        return disputeService.get(disputeId);
    }

    @PatchMapping("/{disputeId}")
    public DisputeResponse update(@PathVariable UUID disputeId, @Valid @RequestBody DisputeUpdateRequest request) {
        return disputeService.update(disputeId, request);
    }
}
