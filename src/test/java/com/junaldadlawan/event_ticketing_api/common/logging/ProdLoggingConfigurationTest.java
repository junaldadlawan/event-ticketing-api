package com.junaldadlawan.event_ticketing_api.common.logging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the "prod logs only important business transactions" rule at the
 * configuration level. {@code application.properties} (shared with dev) sets
 * several specific loggers to DEBUG, and a specific logger beats the root
 * level - so every one of them must be explicitly reset in
 * {@code application-prod.properties}. This test merges the two files the
 * same way the prod profile does (prod overrides base) and fails if any
 * {@code logging.level.*} is noisier than WARN other than the deliberately
 * allow-listed ones - which also catches a new dev-only logger added to the
 * base file later and forgotten in prod.
 * <p>
 * Deliberately a plain properties check, not a {@code @SpringBootTest}:
 * loading a real prod-profile context here would reconfigure the logging
 * system for every test that runs after it in the same JVM.
 */
class ProdLoggingConfigurationTest {

    private static final Set<String> QUIET = Set.of("WARN", "ERROR", "OFF", "FATAL");

    /** logger -> the only level it is allowed to be noisier than WARN at in prod. */
    private static final Map<String, String> ALLOWED_NOISIER = Map.of(
            "logging.level.AUDIT", "INFO",
            "logging.level.com.junaldadlawan.event_ticketing_api.EventTicketingApiApplication", "INFO",
            "logging.level.org.flywaydb.core.internal.command.DbMigrate", "INFO");

    private static Map<String, String> effectiveProd;

    @BeforeAll
    static void mergeBaseAndProd() throws IOException {
        Properties base = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        Properties prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
        effectiveProd = new TreeMap<>();
        base.forEach((key, value) -> effectiveProd.put(String.valueOf(key), String.valueOf(value)));
        prod.forEach((key, value) -> effectiveProd.put(String.valueOf(key), String.valueOf(value)));
    }

    @Test
    void everyLoggerIsQuietExceptTheAllowListed() {
        Map<String, String> noisy = new TreeMap<>();
        effectiveProd.forEach((name, level) -> {
            if (name.startsWith("logging.level.")
                    && !QUIET.contains(level.trim().toUpperCase())
                    && !level.trim().equalsIgnoreCase(ALLOWED_NOISIER.get(name))) {
                noisy.put(name, level);
            }
        });
        assertThat(noisy).as("loggers noisier than WARN in the prod profile").isEmpty();
    }

    @Test
    void baseFileReallyDoesSetNoisyLoggers_soTheGuardAboveIsMeaningful() throws IOException {
        Properties base = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        assertThat(base.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("DEBUG");
    }

    @Test
    void prodUsesJsonLogs_andNoPerRequestLine_andAuditStaysOn() {
        assertThat(effectiveProd.get("logging.structured.format.console")).isEqualTo("logstash");
        assertThat(effectiveProd.get("app.logging.request-log.enabled")).isEqualTo("false");
        assertThat(effectiveProd.get("logging.level.AUDIT")).isEqualTo("INFO");
        assertThat(effectiveProd.get("logging.level.root")).isEqualTo("WARN");
        assertThat(effectiveProd.get("spring.jpa.show-sql")).isEqualTo("false");
    }
}
