package com.junaldadlawan.event_ticketing_api.platformfee.service;

import com.junaldadlawan.event_ticketing_api.platformfee.dto.EffectivePlatformFeeResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleRequest;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;

import java.util.List;
import java.util.UUID;

public interface PlatformFeeService {

    /**
     * The fee for a purchase whose ticket total (after promo discounts) is {@code ticketTotal} minor units in
     * {@code currency}, for an event of {@code organizationId}. The most specific live rule applies: the event's, else the
     * organization's, else the platform default; none = no fee. A zero ticket total never pays a fee, and a FLAT rule in a
     * different currency than the order is skipped (no fee) rather than mixing currencies.
     */
    PlatformFeeQuote quote(UUID organizationId, UUID eventId, long ticketTotal, String currency);

    /** Admin only. All live rules, optionally only those of one scope. */
    List<PlatformFeeRuleResponse> list(FeeScope scope);

    /** Admin only. Creates or replaces the rule of a scope ({@code scopeId} null for PLATFORM). 404 if the organization/event does not exist. */
    PlatformFeeRuleResponse upsert(FeeScope scope, UUID scopeId, PlatformFeeRuleRequest request);

    /** Admin only. Removes the rule of a scope so the next less specific one applies again. 404 if there is none. */
    void delete(FeeScope scope, UUID scopeId);

    /** Admin only. The rule that currently applies to an event. */
    EffectivePlatformFeeResponse effective(UUID eventId);
}
