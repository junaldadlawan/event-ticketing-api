package com.junaldadlawan.event_ticketing_api.organization.security;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The super admin keeps every admin power, and is the only one who may act on an admin's account. */
class OrganizationAccessGuardAccountRulesTest {

    private final OrganizationAccessGuard guard = new OrganizationAccessGuard(Mockito.mock(OrganizationMemberRepository.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void callerIs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static User account(Role role) {
        return User.builder().id(UUID.randomUUID()).firstName("t").lastName("User").email("t@demo.test").role(role).build();
    }

    // ---- who counts as an admin ----

    @Test
    void anAdminAndTheSuperAdminBothPassTheAdminCheck_aCustomerDoesNot() {
        callerIs("ADMIN");
        assertThat(guard.isAdmin()).isTrue();
        assertThat(guard.isSuperAdmin()).isFalse();
        assertThatCode(guard::requireAdmin).doesNotThrowAnyException();

        callerIs("SUPER_ADMIN");
        assertThat(guard.isAdmin()).isTrue();
        assertThat(guard.isSuperAdmin()).isTrue();
        assertThatCode(guard::requireAdmin).doesNotThrowAnyException();

        callerIs("CUSTOMER");
        assertThat(guard.isAdmin()).isFalse();
        assertThat(guard.isSuperAdmin()).isFalse();
        assertThatThrownBy(guard::requireAdmin).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nobodySignedIn_isNeitherAdminNorSuperAdmin() {
        SecurityContextHolder.clearContext();

        assertThat(guard.isAdmin()).isFalse();
        assertThat(guard.isSuperAdmin()).isFalse();
    }

    @Test
    void requireSuperAdmin_letsOnlyTheSuperAdminThrough() {
        callerIs("SUPER_ADMIN");
        assertThatCode(guard::requireSuperAdmin).doesNotThrowAnyException();

        for (String role : new String[] {"ADMIN", "CUSTOMER", null}) {
            callerIs(role);
            assertThatThrownBy(guard::requireSuperAdmin).isInstanceOf(ForbiddenException.class).hasMessage("Only the super admin can do this");
        }
    }

    // ---- acting on another account ----

    @Test
    void anAdminCannotActOnAnotherAdmin_butCanOnACustomer() {
        callerIs("ADMIN");

        assertThatThrownBy(() -> guard.requireMayManageAccount(account(Role.ADMIN), "suspend"))
                .isInstanceOf(ForbiddenException.class).hasMessage("Only the super admin can suspend an admin account");
        assertThatCode(() -> guard.requireMayManageAccount(account(Role.CUSTOMER), "suspend")).doesNotThrowAnyException();
    }

    @Test
    void theSuperAdminCanActOnAnAdminAndOnACustomer() {
        callerIs("SUPER_ADMIN");

        assertThatCode(() -> guard.requireMayManageAccount(account(Role.ADMIN), "suspend")).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireMayManageAccount(account(Role.CUSTOMER), "remove")).doesNotThrowAnyException();
    }

    @Test
    void nobodyCanActOnTheSuperAdmin_notEvenTheSuperAdmin() {
        for (String role : new String[] {"ADMIN", "SUPER_ADMIN"}) {
            callerIs(role);
            assertThatThrownBy(() -> guard.requireMayManageAccount(account(Role.SUPER_ADMIN), "change the role of"))
                    .isInstanceOf(ForbiddenException.class).hasMessage("Nobody can change the role of the super admin account");
        }
    }
}
