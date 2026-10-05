package com.junaldadlawan.event_ticketing_api.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(originalLevel);
        MDC.clear();
    }

    private String run(RequestIdFilter filter, MockHttpServletRequest request, MockHttpServletResponse response,
                       AtomicReference<String> mdcSeenInsideChain) throws Exception {
        FilterChain chain = (req, res) -> mdcSeenInsideChain.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID));
        filter.doFilter(request, response, chain);
        return response.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void noInboundId_generatesOne_putsItInMdcDuringTheRequest_echoesIt_andClearsAfter() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String header = run(new RequestIdFilter(false), new MockHttpServletRequest("GET", "/api/v1/events"), response, seen);

        assertThat(header).isNotBlank();
        assertThat(seen.get()).isEqualTo(header);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void validInboundId_isHonored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(RequestIdFilter.HEADER, "client-trace-12345");
        AtomicReference<String> seen = new AtomicReference<>();

        String header = run(new RequestIdFilter(false), request, new MockHttpServletResponse(), seen);

        assertThat(header).isEqualTo("client-trace-12345");
        assertThat(seen.get()).isEqualTo("client-trace-12345");
    }

    @Test
    void junkInboundId_isReplaced() throws Exception {
        for (String junk : new String[]{"short", "has spaces in it!", "x".repeat(200), "bad;id=1\tvalue"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
            request.addHeader(RequestIdFilter.HEADER, junk);

            String header = run(new RequestIdFilter(false), request, new MockHttpServletResponse(), new AtomicReference<>());

            assertThat(header).isNotEqualTo(junk).hasSizeBetween(8, 64);
        }
    }

    @Test
    void requestLogEnabled_writesOneSummaryLine_withoutBodyOrHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setQueryString("a=1");
        request.setContent("{\"password\":\"secret\"}".getBytes());
        request.addHeader("Authorization", "Bearer super-secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        run(new RequestIdFilter(true), request, response, new AtomicReference<>());

        assertThat(appender.list).hasSize(1);
        String line = appender.list.get(0).getFormattedMessage();
        assertThat(line).startsWith("POST /api/v1/auth/login?a=1 -> 401");
        assertThat(line).doesNotContain("secret").doesNotContain("Bearer");
    }

    @Test
    void requestLogDisabled_writesNothing() throws Exception {
        run(new RequestIdFilter(false), new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void mdcIsClearedEvenIfTheChainThrows() {
        RequestIdFilter filter = new RequestIdFilter(false);
        FilterChain boom = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), boom);
        } catch (Exception expected) {
            // expected
        }

        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }
}
