package com.junaldadlawan.event_ticketing_api.platformfee.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.EffectivePlatformFeeResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleRequest;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.entity.PlatformFeeRule;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
import com.junaldadlawan.event_ticketing_api.platformfee.repository.PlatformFeeRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformFeeServiceImpl implements PlatformFeeService {

    private final PlatformFeeRuleRepository ruleRepository;
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;

    // ---- the fee itself ----

    @Override
    public PlatformFeeQuote quote(UUID organizationId, UUID eventId, long ticketTotal, String currency, int ticketCount) {
        if (ticketTotal <= 0) {
            return PlatformFeeQuote.NONE;
        }
        PlatformFeeRule rule = resolve(organizationId, eventId).orElse(null);
        if (rule == null) {
            return PlatformFeeQuote.NONE;
        }
        // A rule can hold a percentage, a fixed amount per TICKET, or both (then the fee is the two added); a fixed amount in
        // another currency than the order is skipped rather than mixing currencies.
        boolean flatApplies = rule.getFlatAmount() != null && rule.getFlatCurrency() != null && rule.getFlatCurrency().equalsIgnoreCase(currency);
        if (rule.getFlatAmount() != null && !flatApplies) {
            log.warn("Platform fee rule {} has a flat amount in {} but the order is in {}; the flat part is skipped",
                    rule.getId(), rule.getFlatCurrency(), currency);
        }
        if (rule.getPercentage() == null && !flatApplies) {
            return PlatformFeeQuote.NONE;
        }
        long fee = 0;
        if (rule.getPercentage() != null) {
            fee += BigDecimal.valueOf(ticketTotal).multiply(rule.getPercentage())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        }
        if (flatApplies) {
            fee += Math.multiplyExact(rule.getFlatAmount(), (long) Math.max(ticketCount, 0));
        }
        return new PlatformFeeQuote(fee, rule.getScope(), rule.getType(), rule.getPercentage(), rule.getFlatAmount(), rule.getPayer());
    }

    /** Event rule, else organization rule, else the platform default. */
    private Optional<PlatformFeeRule> resolve(UUID organizationId, UUID eventId) {
        Optional<PlatformFeeRule> rule = eventId == null ? Optional.empty()
                : ruleRepository.findByScopeAndScopeIdAndDeletedAtIsNull(FeeScope.EVENT, eventId);
        if (rule.isEmpty() && organizationId != null) {
            rule = ruleRepository.findByScopeAndScopeIdAndDeletedAtIsNull(FeeScope.ORGANIZATION, organizationId);
        }
        if (rule.isEmpty()) {
            rule = ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope.PLATFORM);
        }
        return rule;
    }

    // ---- admin management ----

    @Override
    public List<PlatformFeeRuleResponse> list(FeeScope scope) {
        accessGuard.requireAdmin();
        List<PlatformFeeRule> rules = scope == null
                ? ruleRepository.findByDeletedAtIsNullOrderByScopeAscCreatedAtAsc()
                : ruleRepository.findByScopeAndDeletedAtIsNullOrderByCreatedAtAsc(scope);
        return rules.stream().map(PlatformFeeRuleResponse::from).toList();
    }

    @Override
    public PlatformFeeRuleResponse upsert(FeeScope scope, UUID scopeId, PlatformFeeRuleRequest request) {
        accessGuard.requireAdmin();
        requireTargetExists(scope, scopeId);
        validate(request);

        PlatformFeeRule rule = findLive(scope, scopeId).orElseGet(() -> PlatformFeeRule.builder()
                .scope(scope).scopeId(scope == FeeScope.PLATFORM ? null : scopeId).build());
        rule.setType(request.percentage() == null ? FeeType.FLAT : request.flatAmount() == null ? FeeType.PERCENTAGE : FeeType.COMBINED);
        rule.setPercentage(request.percentage());
        rule.setFlatAmount(request.flatAmount() == null ? null : request.flatAmount().amount());
        rule.setFlatCurrency(request.flatAmount() == null ? null : request.flatAmount().currency());
        rule.setPayer(request.payer() == null ? GatewayFeePayer.CUSTOMER : request.payer());
        PlatformFeeRule saved = ruleRepository.save(rule);
        BusinessAuditLogger.record("platform_fee.set", "PlatformFeeRule", saved.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "scope=" + scope + (scopeId == null ? "" : " scopeId=" + scopeId) + " type=" + saved.getType()
                        + " percentage=" + saved.getPercentage() + " flat=" + saved.getFlatAmount() + " " + saved.getFlatCurrency()
                        + " payer=" + saved.getPayer());
        return PlatformFeeRuleResponse.from(saved);
    }

    @Override
    public void delete(FeeScope scope, UUID scopeId) {
        accessGuard.requireAdmin();
        PlatformFeeRule rule = findLive(scope, scopeId).orElseThrow(() -> new ResourceNotFoundException(
                "No platform fee rule for " + scope + (scopeId == null ? "" : " " + scopeId)));
        rule.markDeleted();
        ruleRepository.save(rule);
        BusinessAuditLogger.record("platform_fee.removed", "PlatformFeeRule", rule.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "scope=" + scope + (scopeId == null ? "" : " scopeId=" + scopeId));
    }

    @Override
    public EffectivePlatformFeeResponse effective(UUID eventId) {
        accessGuard.requireAdmin();
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        PlatformFeeRuleResponse rule = resolve(event.getOrganizationId(), event.getId()).map(PlatformFeeRuleResponse::from).orElse(null);
        return new EffectivePlatformFeeResponse(event.getId(), event.getOrganizationId(), rule);
    }

    private Optional<PlatformFeeRule> findLive(FeeScope scope, UUID scopeId) {
        return scope == FeeScope.PLATFORM
                ? ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope.PLATFORM)
                : ruleRepository.findByScopeAndScopeIdAndDeletedAtIsNull(scope, scopeId);
    }

    private void requireTargetExists(FeeScope scope, UUID scopeId) {
        if (scope == FeeScope.ORGANIZATION && !organizationRepository.existsById(scopeId)) {
            throw new ResourceNotFoundException("Organization " + scopeId + " not found");
        }
        if (scope == FeeScope.EVENT && eventRepository.findByIdAndDeletedAtIsNull(scopeId).isEmpty()) {
            throw new ResourceNotFoundException("Event " + scopeId + " not found");
        }
    }

    private static void validate(PlatformFeeRuleRequest request) {
        if (request.payer() == GatewayFeePayer.PLATFORM) {
            throw new BadRequestException("payer must be CUSTOMER or ORGANIZATION: the platform cannot be the payer of its own fee");
        }
        if (request.percentage() == null && request.flatAmount() == null) {
            throw new BadRequestException("percentage or flatAmount is required (or both)");
        }
    }
}
