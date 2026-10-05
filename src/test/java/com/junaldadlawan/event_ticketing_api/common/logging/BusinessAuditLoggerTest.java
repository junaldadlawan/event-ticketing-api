package com.junaldadlawan.event_ticketing_api.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessAuditLoggerTest {

    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger(BusinessAuditLogger.LOGGER_NAME);
        originalLevel = auditLogger.getLevel();
        auditLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
        auditLogger.setLevel(originalLevel);
        SecurityContextHolder.clearContext();
    }

    @Test
    void success_isInfo_withStructuredFieldsInMdc() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        BusinessAuditLogger.recordAs(buyerId, "checkout.completed", "Order", orderId, Outcome.SUCCESS, "total=4500 USD");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("action=checkout.completed")
                .contains("actor=" + buyerId)
                .contains("target=Order:" + orderId)
                .contains("outcome=SUCCESS")
                .contains("detail=total=4500 USD");
        assertThat(event.getMDCPropertyMap())
                .containsEntry("log_type", "audit")
                .containsEntry("action", "checkout.completed")
                .containsEntry("actorId", buyerId.toString())
                .containsEntry("targetType", "Order")
                .containsEntry("targetId", orderId.toString())
                .containsEntry("outcome", "SUCCESS");
    }

    @Test
    void failure_isWarn() {
        BusinessAuditLogger.recordAs(null, "auth.login", "User", null, Outcome.FAILURE, "wrong password");

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("actor=anonymous").contains("target=User:-");
    }

    @Test
    void mdcFields_areRemovedAfterTheCall() {
        MDC.put("requestId", "keep-me-1234");

        BusinessAuditLogger.record("event.published", "Event", UUID.randomUUID(), Outcome.SUCCESS);

        assertThat(MDC.get("log_type")).isNull();
        assertThat(MDC.get("action")).isNull();
        assertThat(MDC.get("actorId")).isNull();
        assertThat(MDC.get("requestId")).isEqualTo("keep-me-1234");
        MDC.remove("requestId");
    }

    @Test
    void record_usesTheAuthenticatedCallerAsActor() {
        UUID callerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of()));

        BusinessAuditLogger.record("event.published", "Event", UUID.randomUUID(), Outcome.SUCCESS);

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("actorId", callerId.toString());
    }

    @Test
    void record_withNoAuthentication_isAnonymous() {
        BusinessAuditLogger.record("event.published", "Event", UUID.randomUUID(), Outcome.SUCCESS);

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("actorId", "anonymous");
    }

    @Test
    void controlCharacters_cannotForgeExtraLogLines() {
        BusinessAuditLogger.recordAs("attacker", "auth.login", "User", "x",
                Outcome.FAILURE, "bad\nAUDIT action=admin.granted actor=attacker\r");

        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("\n").doesNotContain("\r");
        assertThat(appender.list).hasSize(1);
    }

    @Test
    void longValues_areCapped() {
        BusinessAuditLogger.record("x", "Y", "z", Outcome.SUCCESS, "a".repeat(5000));

        assertThat(appender.list.get(0).getFormattedMessage().length()).isLessThan(600);
    }

    @Test
    void maskEmail_keepsFirstCharAndDomainOnly() {
        assertThat(BusinessAuditLogger.maskEmail("jordan.rivera@example.com")).isEqualTo("j***@example.com");
        assertThat(BusinessAuditLogger.maskEmail("nonsense")).isEqualTo("***");
        assertThat(BusinessAuditLogger.maskEmail(null)).isEqualTo("-");
    }
}
