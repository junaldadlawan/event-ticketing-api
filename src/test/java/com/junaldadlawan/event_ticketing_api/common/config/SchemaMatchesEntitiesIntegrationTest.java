package com.junaldadlawan.event_ticketing_api.common.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Production starts with {@code spring.jpa.hibernate.ddl-auto=validate}: Hibernate checks every entity against the schema Flyway built and the
 * app refuses to start on any difference (dev uses {@code update}, which quietly hides them: a SMALLINT column read as int broke an ECS deploy).
 * This builds a brand new database from the migrations alone, then starts the whole application on it with validate, so a mismatch fails here.
 */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
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
}
