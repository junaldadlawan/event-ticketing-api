package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutAccountRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutAccountResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutAccount;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutAccountRepository;
import com.junaldadlawan.event_ticketing_api.payout.security.AccountNumberCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutAccountServiceImpl implements PayoutAccountService {

    private final PayoutAccountRepository payoutAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AccountNumberCipher cipher;

    @Override
    @Transactional(readOnly = true)
    public PayoutAccountResponse get(UUID organizationId) {
        requireOrganization(organizationId);
        if (!accessGuard.isAdmin()) {
            UUID callerId = accessGuard.currentUserId();
            if (!accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                    && !accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER)) {
                throw new ForbiddenException("Only the organization's owner, organizer, or an admin may view its payout account");
            }
        }
        return PayoutAccountResponse.from(findLive(organizationId));
    }

    @Override
    @Transactional
    public PayoutAccountResponse set(UUID organizationId, PayoutAccountRequest request) {
        Organization organization = requireOrganization(organizationId);
        requireOwnerOrAdmin(organizationId, "set the payout account");
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ForbiddenException("Organization is not approved");
        }
        String number = request.accountNumber();
        PayoutAccount account = payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId)
                .orElseGet(() -> PayoutAccount.builder().organizationId(organizationId).build());
        account.setBankCode(request.bankCode());
        account.setBankName(request.bankName().trim());
        account.setAccountName(request.accountName().trim());
        account.setAccountNumberEncrypted(cipher.encrypt(number));
        account.setAccountNumberLast4(number.substring(number.length() - 4));
        PayoutAccount saved = payoutAccountRepository.save(account);
        // Only the bank and the last 4 are logged, never the number.
        BusinessAuditLogger.record("payout_account.set", "PayoutAccount", saved.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + organizationId + " bank=" + saved.getBankName() + " last4=" + saved.getAccountNumberLast4());
        return PayoutAccountResponse.from(saved);
    }

    @Override
    @Transactional
    public void remove(UUID organizationId) {
        requireOrganization(organizationId);
        requireOwnerOrAdmin(organizationId, "remove the payout account");
        PayoutAccount account = findLive(organizationId);
        account.markDeleted();
        payoutAccountRepository.save(account);
        BusinessAuditLogger.record("payout_account.removed", "PayoutAccount", account.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "organization=" + organizationId + " last4=" + account.getAccountNumberLast4());
    }

    private PayoutAccount findLive(UUID organizationId) {
        return payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + organizationId + " has no payout account"));
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
