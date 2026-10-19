package com.junaldadlawan.event_ticketing_api.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Tests share the dev database, and since BR-EVENT-006 almost every write to an event (ticket types, promo codes,
 * policies, ...) adds an audit row tagged with the event. Test classes hard-delete the events they create in their
 * tear-down; this removes the audit rows those events leave behind, after every Spring test class. The app itself
 * never hard-deletes an event (only soft-deletes), so only rows of test events can match.
 * Registered in src/test/resources/META-INF/spring.factories.
 */
public class OrphanEventAuditRowsCleanup implements TestExecutionListener {

    @Override
    public void afterTestClass(TestContext testContext) {
        if (!testContext.hasApplicationContext()) {
            return;
        }
        // slice tests (@WebMvcTest) have no database
        testContext.getApplicationContext().getBeanProvider(JdbcTemplate.class).ifAvailable(jdbc -> jdbc.update(
                "delete from audit_log_entries a where a.event_id is not null"
                        + " and not exists (select 1 from events e where e.id = a.event_id)"));
    }
}
