package com.lexpro.lexprobackend.auth.service;

import com.lexpro.lexprobackend.auth.config.LoginRateLimitProperties;
import com.lexpro.lexprobackend.common.error.RateLimitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoginRateLimiter {

    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, AttemptBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    @Autowired
    public LoginRateLimiter(LoginRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void acquire(String username, String remoteAddress) {
        if (!properties.isEnabled()) return;
        long now = clock.millis();
        cleanupIfNeeded(now);
        consume(usernameKey(username), properties.getUsernameMaxAttempts(), now);
        consume(addressKey(remoteAddress), properties.getAddressMaxAttempts(), now);
    }

    public void recordSuccess(String username) {
        if (properties.isEnabled()) buckets.remove(usernameKey(username));
    }

    private void consume(String key, int limit, long now) {
        if (!buckets.containsKey(key) && buckets.size() >= properties.getMaxTrackedEntries()) {
            throw new RateLimitException(Math.max(1, properties.getBlockDuration().toSeconds()));
        }
        Decision decision = new Decision();
        buckets.compute(key, (ignored, existing) -> {
            AttemptBucket bucket = existing == null ? new AttemptBucket() : existing;
            long windowStart = now - properties.getWindow().toMillis();
            while (!bucket.attempts.isEmpty() && bucket.attempts.peekFirst() <= windowStart) {
                bucket.attempts.removeFirst();
            }
            if (bucket.blockedUntil > now) {
                decision.retryAfterMillis = bucket.blockedUntil - now;
                return bucket;
            }
            if (bucket.attempts.size() >= limit) {
                bucket.blockedUntil = now + properties.getBlockDuration().toMillis();
                decision.retryAfterMillis = properties.getBlockDuration().toMillis();
                return bucket;
            }
            bucket.blockedUntil = 0;
            bucket.attempts.addLast(now);
            return bucket;
        });
        if (decision.retryAfterMillis > 0) {
            long seconds = Math.max(1, (decision.retryAfterMillis + 999) / 1000);
            throw new RateLimitException(seconds);
        }
    }

    private void cleanupIfNeeded(long now) {
        if (cleanupCounter.incrementAndGet() % 128 != 0 && buckets.size() < properties.getMaxTrackedEntries()) return;
        long expiry = now - properties.getWindow().toMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().blockedUntil <= now
                && (entry.getValue().attempts.isEmpty() || entry.getValue().attempts.peekLast() <= expiry));
    }

    private String usernameKey(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return "username:" + normalized;
    }

    private String addressKey(String remoteAddress) {
        return "address:" + (remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress);
    }

    private static final class AttemptBucket {
        private final ArrayDeque<Long> attempts = new ArrayDeque<>();
        private long blockedUntil;
    }

    private static final class Decision {
        private long retryAfterMillis;
    }
}
