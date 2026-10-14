package com.junaldadlawan.event_ticketing_api.organization.security;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
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

    /** An ADMIN or the SUPER_ADMIN: the super admin keeps every admin power. */
    public boolean isAdmin() {
        return hasAuthority("ROLE_ADMIN") || hasAuthority("ROLE_SUPER_ADMIN");
    }

    public boolean isSuperAdmin() {
        return hasAuthority("ROLE_SUPER_ADMIN");
    }

    private boolean hasAuthority(String authorityName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return isAuthenticated(authentication)
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(authorityName));
    }

    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new ForbiddenException("Only the super admin can do this");
        }
    }

    /**
     * Who may suspend, reinstate, remove, delete or change the role of another account: nobody for the SUPER_ADMIN's
     * account, only the super admin for an ADMIN's, any admin for everyone else. {@code action} is the verb phrase
     * used in the message, for example "suspend" or "change the role of".
     */
    public void requireMayManageAccount(User target, String action) {
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new ForbiddenException("Nobody can " + action + " the super admin account");
        }
        if (target.getRole() == Role.ADMIN && !isSuperAdmin()) {
            throw new ForbiddenException("Only the super admin can " + action + " an admin account");
        }
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
