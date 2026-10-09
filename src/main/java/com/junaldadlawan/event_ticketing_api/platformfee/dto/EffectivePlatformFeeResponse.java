package com.junaldadlawan.event_ticketing_api.platformfee.dto;

import java.io.Serializable;
import java.util.UUID;

/** Which rule applies to an event right now. {@code rule} is null when no rule applies (no fee is charged). */
public record EffectivePlatformFeeResponse(
        UUID eventId,
        UUID organizationId,
        PlatformFeeRuleResponse rule) implements Serializable {
}
