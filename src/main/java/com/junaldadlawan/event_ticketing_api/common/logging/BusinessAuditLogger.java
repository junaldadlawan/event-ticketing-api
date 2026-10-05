package com.junaldadlawan.event_ticketing_api.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Writes one structured "audit log" line per important business
 * transaction to the dedicated {@code AUDIT} logger.
 * <p>
 * Separate from the database audit trail ({@code AuditLogService} /
 * {@code audit_log_entries}): this is the log stream, tagged
 * {@code log_type=audit} so CloudWatch Logs Insights can filter it out of the
 * rest of stdout. In {@code prod} it is JSON (every MDC field becomes a
 * top-level JSON field); in {@code dev} it is a readable text line.
 * <p>
 * A static utility on purpose (not an injected bean): audit lines are a
 * pure side effect, and keeping them out of the constructors means adding
 * one to a service doesn't ripple into every test that builds that service
 * by hand. Tests assert on the {@code AUDIT} logger's output instead.
 * <p>
 * Never throws (a logging failure must not break the transaction it
 * describes), never logs secrets, and strips control characters from every
 * value so user-supplied text can't forge extra log lines.
 */
public final class BusinessAuditLogger {

    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    public static final String LOGGER_NAME = "AUDIT";

    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);
    private static final int MAX_VALUE_LENGTH = 200;

    private BusinessAuditLogger() {
    }

    /** Records an action by the currently authenticated caller (or "anonymous"). */
    public static void record(String action, String targetType, Object targetId, Outcome outcome) {
        write(currentActor(), action, targetType, targetId, outcome, null);
    }

    public static void record(String action, String targetType, Object targetId, Outcome outcome, String detail) {
        write(currentActor(), action, targetType, targetId, outcome, detail);
    }

    /** For actions where the actor is known but not (yet) authenticated, e.g. login. */
    public static void recordAs(Object actorId, String action, String targetType, Object targetId,
                                Outcome outcome, String detail) {
        write(actorId == null ? "anonymous" : String.valueOf(actorId), action, targetType, targetId, outcome, detail);
    }

    /** {@code jordan@example.com} -> {@code j***@example.com}: enough to correlate, not enough to harvest. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "-";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static void write(String actor, String action, String targetType, Object targetId,
                              Outcome outcome, String detail) {
        try (MDC.MDCCloseable ignoredType = MDC.putCloseable("log_type", "audit");
             MDC.MDCCloseable ignoredAction = MDC.putCloseable("action", clean(action));
             MDC.MDCCloseable ignoredActor = MDC.putCloseable("actorId", clean(actor));
             MDC.MDCCloseable ignoredTargetType = MDC.putCloseable("targetType", clean(targetType));
             MDC.MDCCloseable ignoredTargetId = MDC.putCloseable("targetId", clean(targetId));
             MDC.MDCCloseable ignoredOutcome = MDC.putCloseable("outcome", outcome == null ? "UNKNOWN" : outcome.name())) {
            String message = "AUDIT action=" + clean(action)
                    + " actor=" + clean(actor)
                    + " target=" + clean(targetType) + ":" + clean(targetId)
                    + " outcome=" + (outcome == null ? "UNKNOWN" : outcome.name())
                    + (detail == null ? "" : " detail=" + clean(detail));
            if (outcome == Outcome.FAILURE) {
                AUDIT.warn(message);
            } else {
                AUDIT.info(message);
            }
        } catch (RuntimeException e) {
            // never let audit logging break the business transaction
        }
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return authentication.getName();
    }

    /** Null-safe; strips CR/LF and other control characters; caps length. */
    static String clean(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).replaceAll("[\\p{Cntrl}]", "_");
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) : text;
    }
}
