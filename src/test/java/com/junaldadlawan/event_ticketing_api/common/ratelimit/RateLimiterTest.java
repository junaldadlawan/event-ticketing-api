package com.junaldadlawan.event_ticketing_api.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("login", "1.2.3.4", 3)).as("call %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("login", "1.2.3.4", 3)).as("4th over limit of 3").isFalse();
    }

    @Test
    void bucketsAndClientsAreCountedSeparately() {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.tryAcquire("login", "A", 1)).isTrue();
        assertThat(limiter.tryAcquire("login", "A", 1)).isFalse();   // A is now over
        assertThat(limiter.tryAcquire("login", "B", 1)).isTrue();    // a different client has its own budget
        assertThat(limiter.tryAcquire("checkout", "A", 1)).isTrue(); // a different bucket has its own budget
    }

    @Test
    void clientIpPrefersTheLastForwardedHopThenFallsBackToRemoteAddr() {
        HttpServletRequest forwarded = mock(HttpServletRequest.class);
        when(forwarded.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");
        assertThat(RateLimiter.clientIp(forwarded)).isEqualTo("10.0.0.1");

        HttpServletRequest direct = mock(HttpServletRequest.class);
        when(direct.getHeader("X-Forwarded-For")).thenReturn(null);
        when(direct.getRemoteAddr()).thenReturn("127.0.0.1");
        assertThat(RateLimiter.clientIp(direct)).isEqualTo("127.0.0.1");
    }
}
