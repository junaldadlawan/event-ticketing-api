package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutCardServiceImpl implements PayoutCardService {

    private final PayoutCardRepository payoutCardRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    @Transactional(readOnly = true)
    public List<PayoutCardResponse> list(UUID organizationId) {
        requireOrganization(organizationId);
        if (!accessGuard.isAdmin()) {
            UUID callerId = accessGuard.currentUserId();
            if (!accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                    && !accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER)) {
                throw new ForbiddenException("Only the organization's owner, organizer, or an admin may view its payout cards");
            }
        }
        return payoutCardRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(organizationId).stream()
                .map(PayoutCardResponse::from).toList();
    }

    @Override
    @Transactional
    public PayoutCardResponse add(UUID organizationId, PayoutCardRequest request) {
        Organization organization = requireOrganization(organizationId);
        requireOwnerOrAdmin(organizationId, "add a payout card");
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ForbiddenException("Organization is not approved");
        }
        if (YearMonth.of(request.expYear(), request.expMonth()).isBefore(YearMonth.now(ZoneOffset.UTC))) {
            throw new BadRequestException("The card has expired");
        }

        boolean first = payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(organizationId) == 0;
        boolean primary = first || Boolean.TRUE.equals(request.primary());
        if (primary && !first) {
            demoteCurrentPrimary(organizationId);
        }
        PayoutCard saved = payoutCardRepository.save(PayoutCard.builder()
                .organizationId(organizationId)
                .holderName(request.holderName().trim())
                .brand(request.brand().trim())
                .last4(request.last4())
                .expMonth(request.expMonth())
                .expYear(request.expYear())
                .paymentToken(request.paymentToken())
                .primary(primary)
                .build());
        // Only the last 4 are logged, never the token.
        BusinessAuditLogger.record("payout_card.added", "PayoutCard", saved.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + organizationId + " brand=" + saved.getBrand() + " last4=" + saved.getLast4() + " primary=" + primary);
        return PayoutCardResponse.from(saved);
    }

    @Override
    @Transactional
    public PayoutCardResponse setPrimary(UUID cardId) {
        PayoutCard card = getOrThrow(cardId);
        requireOwnerOrAdmin(card.getOrganizationId(), "change the primary payout card");
        if (!card.isPrimary()) {
            demoteCurrentPrimary(card.getOrganizationId());
            card.setPrimary(true);
            card = payoutCardRepository.save(card);
            BusinessAuditLogger.record("payout_card.primary_set", "PayoutCard", card.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                    "organization=" + card.getOrganizationId() + " last4=" + card.getLast4());
        }
        return PayoutCardResponse.from(card);
    }

    @Override
    @Transactional
    public void remove(UUID cardId) {
        PayoutCard card = getOrThrow(cardId);
        requireOwnerOrAdmin(card.getOrganizationId(), "remove a payout card");
        if (card.isPrimary() && payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(card.getOrganizationId()) > 1) {
            throw new ConflictException("Make another card primary before removing the primary card");
        }
        card.setPrimary(false);
        card.markDeleted();
        payoutCardRepository.save(card);
        BusinessAuditLogger.record("payout_card.removed", "PayoutCard", card.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + card.getOrganizationId() + " last4=" + card.getLast4());
    }

    /** Unsets the old primary and flushes first, so the one-primary unique index never sees two. */
    private void demoteCurrentPrimary(UUID organizationId) {
        payoutCardRepository.findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(organizationId).ifPresent(old -> {
            old.setPrimary(false);
            payoutCardRepository.saveAndFlush(old);
        });
    }

    private PayoutCard getOrThrow(UUID cardId) {
        return payoutCardRepository.findByIdAndDeletedAtIsNull(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout card " + cardId + " not found"));
    }

    private Organization requireOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + organizationId + " not found"));
    }

    private void requireOwnerOrAdmin(UUID organizationId, String action) {
        if (accessGuard.isAdmin()) {
            return;
        }
        if (!accessGuard.hasRole(accessGuard.currentUserId(), organizationId, OrganizationRole.OWNER)) {
            throw new ForbiddenException("Only the organization's owner or an admin may " + action);
        }
    }
}
