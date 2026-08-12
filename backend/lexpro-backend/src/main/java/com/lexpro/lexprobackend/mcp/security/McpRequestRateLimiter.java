package com.lexpro.lexprobackend.mcp.security;

import com.lexpro.lexprobackend.mcp.config.McpProperties;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

public final class McpRequestRateLimiter {

    private final int limitPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public McpRequestRateLimiter(McpProperties properties) {
        this(properties, Clock.systemUTC());
    }

    McpRequestRateLimiter(McpProperties properties, Clock clock) {
        this.limitPerMinute = properties.getRateLimitPerMinute();
        this.clock = clock;
    }

    public boolean allow(String clientId) {
        long minute = clock.millis() / 60_000;
        if (!windows.containsKey(clientId) && windows.size() >= 1_000) {
            windows.clear();
        }
        Window window = windows.computeIfAbsent(clientId, ignored -> new Window(minute));
        synchronized (window) {
            if (window.minute != minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= limitPerMinute) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private static final class Window {
        private long minute;
        private int count;

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
