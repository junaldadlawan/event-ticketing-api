package com.junaldadlawan.event_ticketing_api.common.config;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production starts with {@code spring.jpa.hibernate.ddl-auto=validate}: Hibernate checks every entity against the schema Flyway built and the
 * app refuses to start on any difference. A development database made by {@code update} quietly hides such differences (a SMALLINT column read
 * as int broke an ECS deploy; events.created_by stayed VARCHAR(20) and rejected a 36-character user id on staging). This builds a brand new
 * database from the migrations alone, starts the whole application on it with validate, and creates an event as a real authenticated user.
 * The scratch database is dropped afterwards, so nothing is left behind.
 */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureMockMvc
class SchemaMatchesEntitiesIntegrationTest {

    private static final String DATABASE = "schema_validate_check";
    private static final String SERVER = "jdbc:postgresql://localhost:5432/";

    static {
        recreateDatabase();
    }

    @DynamicPropertySource
    static void scratchDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SERVER + DATABASE);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterAll
    static void dropDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(SERVER + "postgres", "user", "password"); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
        }
    }

    private static void recreateDatabase() {
        try (Connection connection = DriverManager.getConnection(SERVER + "postgres", "user", "password"); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + DATABASE);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the scratch database for the schema check", e);
        }
    }

    @Test
    void theApplicationStartsWithSchemaValidation_onADatabaseBuiltOnlyByFlyway() {
        // the context loading is the assertion: Hibernate validates every entity against the migrated schema
    }

    @Test
    void anAuthenticatedUserCanCreateAnEvent_soTheAuditColumnsHoldAUserId() throws Exception {
        User owner = User.builder().id(UUID.randomUUID()).email("owner-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        Organization organization = organizationRepository.save(Organization.builder().name("Schema Check Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(owner.getId()).documents(List.of()).build());
        OrganizationMember member = OrganizationMember.builder().userId(owner.getId()).organizationId(organization.getId()).build();
        member.getRoles().add(OrganizationRole.OWNER);
        organizationMemberRepository.save(member);
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + jwtService.generateAccessToken(owner))
                        .contentType("application/json")
                        .content("{\"organizationId\":\"" + organization.getId() + "\",\"title\":\"Schema Check Event\",\"description\":\"d\","
                                + "\"category\":\"music\",\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plus(2, ChronoUnit.HOURS) + "\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isCreated());

        // the 36-character user id is what the audit columns hold
        assertThat(jdbc.queryForObject("select created_by from events where title = 'Schema Check Event'", String.class)).isEqualTo(owner.getId().toString());
    }
}
