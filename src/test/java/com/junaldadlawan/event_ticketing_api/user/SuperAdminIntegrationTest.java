package com.junaldadlawan.event_ticketing_api.user;

import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.moderation.repository.ModerationActionRepository;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.AccountStatus;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The super admin through the real security chain: it has every admin power, it alone may suspend, remove, delete or
 * demote an admin (and make someone an admin), an admin may not, nobody may touch the super admin account, the
 * SUPER_ADMIN role cannot be granted through the API, and the database allows only one.
 * The callers are signed in with in-memory users (no super admin row is ever inserted, so a super admin you seeded is
 * left alone); only the accounts acted on are persisted, and removed afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ModerationActionRepository moderationActionRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> actorIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : userIds) {
            moderationActionRepository.findByTargetId(id, PageRequest.of(0, 50)).forEach(moderationActionRepository::delete);
            notificationRepository.findByUserIdOrderByCreatedAtDesc(id).forEach(notificationRepository::delete);
            userRepository.deleteById(id);
        }
        for (UUID id : actorIds) {
            auditLogEntryRepository.findByActorId(id, PageRequest.of(0, 50)).forEach(auditLogEntryRepository::delete);
        }
    }

    private String caller(Role role) {
        User user = User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
        actorIds.add(user.getId());
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private User account(Role role) {
        User saved = userRepository.save(User.builder().firstName("Target").lastName("User")
                .email("target-" + UUID.randomUUID() + "@test.local").passwordHash("x").role(role).build());
        userIds.add(saved.getId());
        return saved;
    }

    private ResultActions moderate(String token, User target, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/moderation-actions").header("Authorization", token).contentType("application/json")
                .content("{\"targetType\":\"USER\",\"targetId\":\"" + target.getId() + "\",\"action\":\"" + action + "\",\"reason\":\"test\"}"));
    }

    private ResultActions setRole(String token, User target, String role) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/{id}", target.getId()).header("Authorization", token)
                .contentType("application/json").content("{\"role\":\"" + role + "\"}"));
    }

    private Role roleOf(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getRole();
    }

    // ---- suspending ----

    @Test
    void anAdminCannotSuspendAnotherAdmin_andNothingChanges() throws Exception {
        User admin = account(Role.ADMIN);

        moderate(caller(Role.ADMIN), admin, "SUSPEND").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the super admin can suspend an admin account"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anAdminCanStillSuspendAndReinstateACustomer() throws Exception {
        User customer = account(Role.CUSTOMER);
        String admin = caller(Role.ADMIN);

        moderate(admin, customer, "SUSPEND").andExpect(status().isCreated());
        assertThat(userRepository.findById(customer.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        moderate(admin, customer, "REINSTATE").andExpect(status().isCreated());
        assertThat(userRepository.findById(customer.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void theSuperAdminCanSuspendReinstateAndRemoveAnAdmin() throws Exception {
        User admin = account(Role.ADMIN);
        String superAdmin = caller(Role.SUPER_ADMIN);

        moderate(superAdmin, admin, "SUSPEND").andExpect(status().isCreated());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        moderate(superAdmin, admin, "REINSTATE").andExpect(status().isCreated());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        moderate(superAdmin, admin, "REMOVE").andExpect(status().isCreated());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void anAdminCannotReinstateOrRemoveAnAdminEither() throws Exception {
        User admin = account(Role.ADMIN);
        String other = caller(Role.ADMIN);

        moderate(other, admin, "REINSTATE").andExpect(status().isForbidden());
        moderate(other, admin, "REMOVE").andExpect(status().isForbidden());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    // ---- deleting and changing roles ----

    @Test
    void deletingAnAdmin_isTheSuperAdminsAlone_deletingACustomerStaysAnAdminPower() throws Exception {
        User admin = account(Role.ADMIN);
        User customer = account(Role.CUSTOMER);

        mockMvc.perform(delete("/api/v1/users/{id}", admin.getId()).header("Authorization", caller(Role.ADMIN))).andExpect(status().isForbidden());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getDeletedAt()).isNull();

        mockMvc.perform(delete("/api/v1/users/{id}", customer.getId()).header("Authorization", caller(Role.ADMIN))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/users/{id}", admin.getId()).header("Authorization", caller(Role.SUPER_ADMIN))).andExpect(status().isNoContent());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void onlyTheSuperAdminMayMakeAnAdmin_orDemoteOne() throws Exception {
        User customer = account(Role.CUSTOMER);
        User admin = account(Role.ADMIN);

        setRole(caller(Role.ADMIN), customer, "ADMIN").andExpect(status().isForbidden());
        setRole(caller(Role.ADMIN), admin, "CUSTOMER").andExpect(status().isForbidden());
        assertThat(roleOf(customer)).isEqualTo(Role.CUSTOMER);
        assertThat(roleOf(admin)).isEqualTo(Role.ADMIN);

        setRole(caller(Role.SUPER_ADMIN), customer, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        setRole(caller(Role.SUPER_ADMIN), admin, "CUSTOMER").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        assertThat(roleOf(customer)).isEqualTo(Role.ADMIN);
        assertThat(roleOf(admin)).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void anAdminCanStillEditACustomersProfileFields_andSendTheCurrentRoleBack() throws Exception {
        User customer = account(Role.CUSTOMER);

        mockMvc.perform(patch("/api/v1/users/{id}", customer.getId()).header("Authorization", caller(Role.ADMIN))
                        .contentType("application/json").content("{\"firstName\":\"Renamed\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Renamed"));
    }

    @Test
    void theSuperAdminRoleCanNeverBeGranted_notEvenByTheSuperAdmin() throws Exception {
        User customer = account(Role.CUSTOMER);

        for (Role role : new Role[] {Role.ADMIN, Role.SUPER_ADMIN}) {
            setRole(caller(role), customer, "SUPER_ADMIN").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("The super admin role cannot be granted"));
        }
        assertThat(roleOf(customer)).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registrationNeverCreatesAnAdminOrASuperAdmin() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@test.local";
        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("{\"firstName\":\"Mallory\",\"lastName\":\"Smith\",\"birthDate\":\"1990-05-17\",\"email\":\"" + email
                                + "\",\"passwordHash\":\"Passw0rd!\",\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        userRepository.findByEmail(email).ifPresent(user -> userIds.add(user.getId()));
    }

    // ---- the super admin keeps every admin power ----

    @Test
    void theSuperAdminPassesEveryAdminOnlyEndpoint_aCustomerDoesNot() throws Exception {
        String superAdmin = caller(Role.SUPER_ADMIN);
        String customer = caller(Role.CUSTOMER);

        for (String path : new String[] {"/api/v1/users", "/api/v1/audit-log", "/api/v1/analytics/platform", "/api/v1/platform-fees"}) {
            mockMvc.perform(get(path).header("Authorization", superAdmin)).andExpect(status().isOk());
            mockMvc.perform(get(path).header("Authorization", customer)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/organizations").header("Authorization", superAdmin)).andExpect(status().isOk());
    }

    // ---- exactly one super admin ----

    @Test
    void theDatabaseAllowsOnlyOneSuperAdmin() {
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'uq_users_single_super_admin'", Integer.class))
                .isEqualTo(1);

        // Two inserts in one transaction that is always rolled back: the second (or the first, if a super admin is already
        // seeded) must be refused by the unique index, and nothing is left behind either way.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            for (int i = 0; i < 2; i++) {
                jdbc.update("insert into users (id, email, password_hash, role, created_at, created_by, first_name, last_name) "
                        + "values (?, ?, 'x', 'SUPER_ADMIN', now(), 'test', 'Super', 'Admin')", UUID.randomUUID(), "super-" + UUID.randomUUID() + "@test.local");
            }
        })).hasMessageContaining("uq_users_single_super_admin");
        assertThat(jdbc.queryForObject("select count(*) from users where role = 'SUPER_ADMIN' and email like 'super-%@test.local'", Integer.class)).isZero();
    }
}
