package com.junaldadlawan.event_ticketing_api.organization.security;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Imperative, service-layer authorization helper for the organization module.
 * Resolves the caller from {@link SecurityContextHolder} rather than relying
 * on method security ({@code @PreAuthorize}), which has no precedent in this
 * codebase.
 */
@Component
@RequiredArgsConstructor
public class OrganizationAccessGuard {

    private final OrganizationMemberRepository organizationMemberRepository;

    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new ForbiddenException("Authentication required");
        }
        return UUID.fromString(authentication.getName());
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return isAuthenticated(authentication)
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException("Admin access required");
        }
    }

    public boolean hasRole(UUID userId, UUID organizationId, OrganizationRole role) {
        return organizationMemberRepository.existsByUserIdAndOrganizationIdAndRolesContaining(userId, organizationId, role);
    }

    /** Ids of every organization where the user is an OWNER or an ORGANIZER. */
    public Set<UUID> managedOrganizationIds(UUID userId) {
        return organizationMemberRepository
                .findDistinctByUserIdAndRolesIn(userId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ORGANIZER))
                .stream()
                .map(OrganizationMember::getOrganizationId)
                .collect(Collectors.toSet());
    }

    public boolean isMember(UUID userId, UUID organizationId) {
        return organizationMemberRepository.existsByUserIdAndOrganizationId(userId, organizationId);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
