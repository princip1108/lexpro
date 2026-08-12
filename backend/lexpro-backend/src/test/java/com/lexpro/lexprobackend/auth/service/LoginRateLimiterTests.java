package com.lexpro.lexprobackend.auth.service;

import com.lexpro.lexprobackend.auth.config.LoginRateLimitProperties;
import com.lexpro.lexprobackend.common.error.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTests {

    private final LoginRateLimitProperties properties = properties();
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            properties, Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void shouldBlockAfterConfiguredUsernameAttempts() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.acquire("admin", "127.0.0.1");
        }

        RateLimitException exception = assertThrows(
                RateLimitException.class,
                () -> limiter.acquire("admin", "127.0.0.1")
        );

        assertEquals(60, exception.getRetryAfterSeconds());
    }

    @Test
    void shouldClearUsernameBucketAfterSuccessfulLogin() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.acquire("admin", "127.0.0.1");
        }

        limiter.recordSuccess(" ADMIN ");

        assertDoesNotThrow(() -> limiter.acquire("admin", "127.0.0.2"));
    }

    @Test
    void shouldEnforceThresholdForConcurrentAttempts() {
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();

        IntStream.range(0, 20).parallel().forEach(attempt -> {
            try {
                limiter.acquire("parallel-user", "127.0.0.1");
                allowed.incrementAndGet();
            } catch (RateLimitException exception) {
                limited.incrementAndGet();
            }
        });

        assertEquals(3, allowed.get());
        assertEquals(17, limited.get());
    }

    private LoginRateLimitProperties properties() {
        LoginRateLimitProperties value = new LoginRateLimitProperties();
        value.setUsernameMaxAttempts(3);
        value.setAddressMaxAttempts(100);
        value.setWindow(Duration.ofMinutes(5));
        value.setBlockDuration(Duration.ofMinutes(1));
        return value;
    }
}
