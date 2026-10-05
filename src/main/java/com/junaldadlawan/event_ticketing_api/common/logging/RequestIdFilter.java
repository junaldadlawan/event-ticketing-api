package com.junaldadlawan.event_ticketing_api.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id so all log lines (application and
 * audit) for one request can be tied together, and optionally logs one
 * summary line per request.
 * <p>
 * Runs before the Spring Security chain (highest precedence) so even a 401
 * has an id. The id is taken from an inbound {@code X-Request-Id} header
 * only if it looks like a plain token (so a client can't inject newlines or
 * huge strings into the logs); otherwise a UUID is generated. It is echoed
 * back in the response header and placed in the MDC, which the dev console
 * pattern and the prod JSON format both include.
 * <p>
 * The per-request line (method, path, status, duration, user) is switched on
 * with {@code app.logging.request-log.enabled} - on in dev, off in prod, where
 * only important business transactions are logged. Request bodies, headers
 * and tokens are never logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";

    private static final Pattern VALID_INBOUND_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    private final boolean requestLogEnabled;

    public RequestIdFilter(@Value("${app.logging.request-log.enabled:false}") boolean requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String requestId = inbound != null && VALID_INBOUND_ID.matcher(inbound).matches()
                ? inbound
                : UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (requestLogEnabled) {
                long millis = (System.nanoTime() - startNanos) / 1_000_000;
                String query = request.getQueryString();
                log.info("{} {}{} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        query == null ? "" : "?" + query, response.getStatus(), millis);
            }
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER_ID);
        }
    }
}
