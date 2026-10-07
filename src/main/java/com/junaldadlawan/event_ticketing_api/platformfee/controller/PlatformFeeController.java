package com.junaldadlawan.event_ticketing_api.platformfee.controller;

import com.junaldadlawan.event_ticketing_api.platformfee.dto.EffectivePlatformFeeResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleRequest;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.service.PlatformFeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The platform fee ("admin cut") rules. Admin only (checked in the service). One rule per scope: the platform default,
 * an organization, or an event; the most specific one applies to a purchase.
 */
@RestController
@RequestMapping("/api/v1/platform-fees")
@RequiredArgsConstructor
public class PlatformFeeController {

    private final PlatformFeeService platformFeeService;

    @GetMapping
    public List<PlatformFeeRuleResponse> list(@RequestParam(required = false) FeeScope scope) {
        return platformFeeService.list(scope);
    }

    @GetMapping("/effective")
    public EffectivePlatformFeeResponse effective(@RequestParam UUID eventId) {
        return platformFeeService.effective(eventId);
    }

    @PutMapping("/default")
    public PlatformFeeRuleResponse setDefault(@Valid @RequestBody PlatformFeeRuleRequest request) {
        return platformFeeService.upsert(FeeScope.PLATFORM, null, request);
    }

    @DeleteMapping("/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDefault() {
        platformFeeService.delete(FeeScope.PLATFORM, null);
    }

    @PutMapping("/organizations/{organizationId}")
    public PlatformFeeRuleResponse setForOrganization(@PathVariable UUID organizationId,
                                                      @Valid @RequestBody PlatformFeeRuleRequest request) {
        return platformFeeService.upsert(FeeScope.ORGANIZATION, organizationId, request);
    }

    @DeleteMapping("/organizations/{organizationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeForOrganization(@PathVariable UUID organizationId) {
        platformFeeService.delete(FeeScope.ORGANIZATION, organizationId);
    }

    @PutMapping("/events/{eventId}")
    public PlatformFeeRuleResponse setForEvent(@PathVariable UUID eventId, @Valid @RequestBody PlatformFeeRuleRequest request) {
        return platformFeeService.upsert(FeeScope.EVENT, eventId, request);
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeForEvent(@PathVariable UUID eventId) {
        platformFeeService.delete(FeeScope.EVENT, eventId);
    }
}
