package com.lexpro.lexprobackend.recommendation.partner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.recommendation.config.PartnerTypicalCaseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class AnalysisTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_TOKEN_LENGTH = 8_192;

    private final PartnerTypicalCaseProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AnalysisTokenService(PartnerTypicalCaseProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    AnalysisTokenService(PartnerTypicalCaseProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IssuedToken issue(String providerAnalysisId, long userId, long caseId, String factText,
                             List<TokenIssue> issues) {
        Instant expiresAt = clock.instant().plus(properties.getAnalysisTtl());
        TokenPayload payload = new TokenPayload(
                PartnerTypicalCaseContract.CONTRACT_VERSION,
                providerAnalysisId,
                userId,
                caseId,
                factHash(factText),
                List.copyOf(issues),
                expiresAt.getEpochSecond()
        );
        try {
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sign(encodedPayload.getBytes(StandardCharsets.US_ASCII)));
            return new IssuedToken(encodedPayload + "." + signature, expiresAt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Analysis token serialization failed", exception);
        }
    }

    public TokenPayload verify(String token, long userId, long caseId, String factText) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new AnalysisTokenException(AnalysisTokenException.Kind.INVALID);
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new AnalysisTokenException(AnalysisTokenException.Kind.INVALID);
        }
        try {
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[1]);
            byte[] expectedSignature = sign(parts[0].getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(suppliedSignature, expectedSignature)) {
                throw new AnalysisTokenException(AnalysisTokenException.Kind.INVALID);
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            TokenPayload payload = objectMapper.readValue(payloadBytes, TokenPayload.class);
            validatePayload(payload);
            if (payload.expiresAtEpochSecond() <= clock.instant().getEpochSecond()) {
                throw new AnalysisTokenException(AnalysisTokenException.Kind.EXPIRED);
            }
            if (payload.userId() != userId || payload.caseId() != caseId
                    || !MessageDigest.isEqual(payload.factHash().getBytes(StandardCharsets.US_ASCII),
                    factHash(factText).getBytes(StandardCharsets.US_ASCII))) {
                throw new AnalysisTokenException(AnalysisTokenException.Kind.BINDING_MISMATCH);
            }
            return payload;
        } catch (AnalysisTokenException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException exception) {
            throw new AnalysisTokenException(AnalysisTokenException.Kind.INVALID);
        }
    }

    private void validatePayload(TokenPayload payload) {
        if (payload == null || !PartnerTypicalCaseContract.CONTRACT_VERSION.equals(payload.contractVersion())
                || payload.providerAnalysisId() == null || payload.providerAnalysisId().isBlank()
                || payload.providerAnalysisId().length() > 200 || payload.userId() <= 0 || payload.caseId() <= 0
                || payload.factHash() == null || payload.factHash().length() != 64
                || payload.issues() == null || payload.issues().isEmpty() || payload.issues().size() > 100
                || payload.expiresAtEpochSecond() <= 0) {
            throw new AnalysisTokenException(AnalysisTokenException.Kind.INVALID);
        }
        for (TokenIssue issue : payload.issues()) {
            if (issue == null || issue.issueText() == null || issue.issueText().isBlank()
                    || issue.issueText().length() > 1_000 || !finiteUnit(issue.reliability())
                    || !finiteUnit(issue.weight())) {
                throw new AnalysisTokenException(AnalysisTokenException.Kind.INVALID);
            }
        }
    }

    private boolean finiteUnit(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    private byte[] sign(byte[] payload) {
        byte[] secret = properties.getTokenSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("Analysis token secret must contain at least 32 bytes");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Analysis token signing is unavailable", exception);
        }
    }

    private String factHash(String factText) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(factText.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record TokenIssue(int issueId, String issueText, double reliability, double weight) {}

    public record TokenPayload(
            String contractVersion,
            String providerAnalysisId,
            long userId,
            long caseId,
            String factHash,
            List<TokenIssue> issues,
            long expiresAtEpochSecond
    ) {}

    public record IssuedToken(String value, Instant expiresAt) {}
}
