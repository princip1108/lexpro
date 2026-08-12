package com.lexpro.lexprobackend.mcp.service;

import com.lexpro.lexprobackend.mcp.config.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
@ConditionalOnProperty(name = "lexpro.mcp.enabled", havingValue = "true")
public class McpInvocationLimiter {

    private final Semaphore global;
    private final int perClientLimit;
    private final ConcurrentHashMap<String, Semaphore> clients = new ConcurrentHashMap<>();

    public McpInvocationLimiter(McpProperties properties) {
        this.global = new Semaphore(properties.getMaxConcurrentRequests());
        this.perClientLimit = properties.getMaxConcurrentPerClient();
    }

    public Permit tryAcquire(String clientId) {
        if (!clients.containsKey(clientId) && clients.size() >= 1_000) {
            clients.clear();
        }
        Semaphore client = clients.computeIfAbsent(clientId, ignored -> new Semaphore(perClientLimit));
        if (!global.tryAcquire()) {
            return null;
        }
        if (!client.tryAcquire()) {
            global.release();
            return null;
        }
        return new Permit(global, client);
    }

    public static final class Permit implements AutoCloseable {

        private final Semaphore global;
        private final Semaphore client;
        private boolean closed;

        private Permit(Semaphore global, Semaphore client) {
            this.global = global;
            this.client = client;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                client.release();
                global.release();
            }
        }
    }
}
