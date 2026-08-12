package com.lexpro.lexprobackend.mcp.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.mcp.config.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class McpClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpClientRegistry.class);
    private static final String SCHEMA_VERSION = "mcp-client-registry-v1";
    private static final long MAX_REGISTRY_BYTES = 1_048_576;
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern TOKEN_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SHA_256 = Pattern.compile("[A-Fa-f0-9]{64}");
    private static final Set<String> ALLOWED_AUTHORITIES = Set.of("AI_EXECUTE");

    private final Path registryPath;
    private final long reloadIntervalNanos;
    private final ObjectMapper objectMapper;
    private volatile List<RegisteredClient> clients;
    private volatile long nextReloadAtNanos;

    public McpClientRegistry(McpProperties properties, ObjectMapper objectMapper) {
        properties.validate();
        Path configuredPath = Path.of(properties.getClientRegistryPath());
        if (!configuredPath.isAbsolute()) {
            throw new IllegalStateException("lexpro.mcp.client-registry-path must be absolute");
        }
        this.registryPath = configuredPath.normalize();
        this.reloadIntervalNanos = properties.getTokenReloadInterval().toNanos();
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.clients = loadRegistry();
        this.nextReloadAtNanos = System.nanoTime() + reloadIntervalNanos;
    }

    public Optional<McpClientIdentity> authenticate(String rawToken) {
        if (rawToken == null || rawToken.length() < 32 || rawToken.length() > 512) {
            return Optional.empty();
        }
        reloadIfDue();
        byte[] candidateHash = sha256(rawToken);
        Instant now = Instant.now();
        McpClientIdentity match = null;
        for (RegisteredClient client : clients) {
            for (RegisteredToken token : client.tokens()) {
                boolean hashMatches = MessageDigest.isEqual(candidateHash, token.sha256());
                if (hashMatches && client.enabled() && token.expiresAt().isAfter(now)) {
                    match = new McpClientIdentity(client.clientId(), client.authorities());
                }
            }
        }
        return Optional.ofNullable(match);
    }

    private void reloadIfDue() {
        long now = System.nanoTime();
        if (now < nextReloadAtNanos) {
            return;
        }
        synchronized (this) {
            now = System.nanoTime();
            if (now < nextReloadAtNanos) {
                return;
            }
            nextReloadAtNanos = now + reloadIntervalNanos;
            try {
                clients = loadRegistry();
            } catch (RuntimeException exception) {
                log.error("MCP client registry reload failed; retaining the last valid registry");
            }
        }
    }

    private List<RegisteredClient> loadRegistry() {
        try {
            if (!Files.isRegularFile(registryPath) || !Files.isReadable(registryPath)) {
                throw new IllegalStateException("MCP client registry is not a readable regular file");
            }
            long size = Files.size(registryPath);
            if (size < 2 || size > MAX_REGISTRY_BYTES) {
                throw new IllegalStateException("MCP client registry size is invalid");
            }
            RegistryDocument document = objectMapper.readValue(Files.readAllBytes(registryPath),
                    RegistryDocument.class);
            return validate(document);
        } catch (IOException exception) {
            throw new IllegalStateException("MCP client registry could not be loaded", exception);
        }
    }

    private List<RegisteredClient> validate(RegistryDocument document) {
        if (document == null || !SCHEMA_VERSION.equals(document.schemaVersion()) || document.clients() == null
                || document.clients().isEmpty() || document.clients().size() > 100) {
            throw new IllegalStateException("MCP client registry document is invalid");
        }
        Set<String> clientIds = new HashSet<>();
        Set<String> tokenHashes = new HashSet<>();
        List<RegisteredClient> validated = new ArrayList<>();
        for (ClientDocument client : document.clients()) {
            if (client == null || client.clientId() == null || !CLIENT_ID.matcher(client.clientId()).matches()
                    || !clientIds.add(client.clientId()) || client.authorities() == null
                    || !ALLOWED_AUTHORITIES.containsAll(client.authorities()) || client.tokens() == null
                    || client.tokens().isEmpty() || client.tokens().size() > 5) {
                throw new IllegalStateException("MCP client registry contains an invalid client");
            }
            Set<String> tokenIds = new HashSet<>();
            List<RegisteredToken> tokens = new ArrayList<>();
            for (TokenDocument token : client.tokens()) {
                if (token == null || token.tokenId() == null || !TOKEN_ID.matcher(token.tokenId()).matches()
                        || !tokenIds.add(token.tokenId()) || token.tokenSha256() == null
                        || !SHA_256.matcher(token.tokenSha256()).matches()
                        || !tokenHashes.add(token.tokenSha256().toLowerCase()) || token.expiresAt() == null) {
                    throw new IllegalStateException("MCP client registry contains an invalid token entry");
                }
                tokens.add(new RegisteredToken(HexFormat.of().parseHex(token.tokenSha256()), token.expiresAt()));
            }
            validated.add(new RegisteredClient(client.clientId(), client.enabled(), Set.copyOf(client.authorities()),
                    List.copyOf(tokens)));
        }
        return List.copyOf(validated);
    }

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record RegistryDocument(String schemaVersion, List<ClientDocument> clients) {}

    private record ClientDocument(String clientId, boolean enabled, Set<String> authorities,
                                  List<TokenDocument> tokens) {}

    private record TokenDocument(String tokenId, String tokenSha256, Instant expiresAt) {}

    private record RegisteredClient(String clientId, boolean enabled, Set<String> authorities,
                                    List<RegisteredToken> tokens) {}

    private record RegisteredToken(byte[] sha256, Instant expiresAt) {
        private RegisteredToken {
            sha256 = sha256.clone();
        }

        @Override
        public byte[] sha256() {
            return sha256.clone();
        }
    }
}
