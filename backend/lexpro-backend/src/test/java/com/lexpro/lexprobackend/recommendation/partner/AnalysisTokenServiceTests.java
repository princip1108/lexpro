package com.lexpro.lexprobackend.recommendation.partner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.recommendation.config.PartnerTypicalCaseProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisTokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-14T01:00:00Z");

    @Test
    void shouldBindSignedTokenToUserCaseAndFactWithoutEmbeddingFactText() {
        AnalysisTokenService service = service(Clock.fixed(NOW, ZoneOffset.UTC));

        AnalysisTokenService.IssuedToken issued = service.issue(
                "analysis-123", 7L, 9L, "敏感案件事实",
                List.of(new AnalysisTokenService.TokenIssue(0, "争议焦点", 0.9, 0.8)));

        AnalysisTokenService.TokenPayload payload = service.verify(
                issued.value(), 7L, 9L, "敏感案件事实");
        assertEquals("analysis-123", payload.providerAnalysisId());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), issued.expiresAt());
        org.junit.jupiter.api.Assertions.assertFalse(issued.value().contains("敏感案件事实"));

        AnalysisTokenException mismatch = assertThrows(AnalysisTokenException.class,
                () -> service.verify(issued.value(), 7L, 9L, "另一案件事实"));
        assertEquals(AnalysisTokenException.Kind.BINDING_MISMATCH, mismatch.getKind());
    }

    @Test
    void shouldRejectTamperedAndExpiredTokens() {
        AnalysisTokenService service = service(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = service.issue("analysis-123", 7L, 9L, "案件事实",
                List.of(new AnalysisTokenService.TokenIssue(0, "焦点", 0.9, 0.8))).value();

        AnalysisTokenException tampered = assertThrows(AnalysisTokenException.class,
                () -> service.verify(token.substring(0, token.length() - 1) + "x", 7L, 9L, "案件事实"));
        assertEquals(AnalysisTokenException.Kind.INVALID, tampered.getKind());

        AnalysisTokenService expiredService = service(
                Clock.fixed(NOW.plus(Duration.ofMinutes(11)), ZoneOffset.UTC));
        AnalysisTokenException expired = assertThrows(AnalysisTokenException.class,
                () -> expiredService.verify(token, 7L, 9L, "案件事实"));
        assertEquals(AnalysisTokenException.Kind.EXPIRED, expired.getKind());
    }

    private AnalysisTokenService service(Clock clock) {
        PartnerTypicalCaseProperties properties = new PartnerTypicalCaseProperties();
        properties.setTokenSecret("0123456789abcdef0123456789abcdef");
        properties.setAnalysisTtl(Duration.ofMinutes(10));
        return new AnalysisTokenService(properties, new ObjectMapper(), clock);
    }
}
