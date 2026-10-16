package com.junaldadlawan.event_ticketing_api.common.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Loads the demo accounts and the sample event ({@code db/seed/demo-data.sql}) at startup, for a DEMO deployment where there is no way to run
 * the script by hand (a private RDS behind ECS). Off unless {@code app.seed.demo-data=true} ({@code APP_SEED_DEMO_DATA}); it runs after Flyway
 * (an ApplicationRunner starts once the context, and so the migrations, is ready). The script is idempotent (it only inserts what is missing, in
 * one transaction) so running it on every start is harmless, but it creates accounts with passwords written in the repository: use it for a
 * demo only and switch it off again afterwards. A failure is logged and the app still starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String SCRIPT = "db/seed/demo-data.sql";

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        log.warn("app.seed.demo-data is ON: loading the demo accounts (known passwords) and the sample event. Switch it off after the first start.");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try {
                // psql ran this script as it is; the JDBC driver also accepts it as one multi-statement command (it has a DO $$ block
                // that a script splitter would break), and the script wraps itself in BEGIN/COMMIT.
                statement.execute(new ClassPathResource(SCRIPT).getContentAsString(StandardCharsets.UTF_8));
                log.warn("Demo data loaded (or already there).");
            } catch (SQLException | java.io.IOException e) {
                log.error("The demo data could not be loaded: {}", e.getMessage());
                rollbackQuietly(statement);
            }
        } catch (SQLException e) {
            log.error("The demo data could not be loaded (no database connection): {}", e.getMessage());
        }
    }

    /** A failed script leaves its BEGIN open on a pooled connection; end it so the connection is clean for the next user. */
    private static void rollbackQuietly(Statement statement) {
        try {
            statement.execute("ROLLBACK");
        } catch (SQLException ignored) {
            // nothing to roll back
        }
    }
}
