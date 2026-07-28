package com.lexpro.lexprobackend.auth.token;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtServiceTests {

    @Test
    void shouldIssueThirtyMinuteTokenWithIdentityAndAuthorities() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        Jwt encoded = Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject("1")
                .build();
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(encoded);
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("https://lexpro.local");
        properties.setAccessTokenTtl(Duration.ofMinutes(30));
        Instant now = Instant.parse("2026-07-29T01:00:00Z");
        JwtService service = new JwtService(encoder, properties, Clock.fixed(now, ZoneOffset.UTC));
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setUsername("admin");
        account.setRealName("System Administrator");
        account.setRoleCode("ADMIN");

        JwtService.IssuedToken result = service.issue(account, List.of("USER_MANAGE"));

        assertEquals("signed-token", result.value());
        assertEquals(now.plus(Duration.ofMinutes(30)), result.expiresAt());
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(captor.capture());
        assertEquals("https://lexpro.local", captor.getValue().getClaims().getIssuer().toString());
        assertEquals("1", captor.getValue().getClaims().getSubject());
        assertEquals("admin", captor.getValue().getClaims().getClaim("username"));
        assertEquals(List.of("ROLE_ADMIN", "USER_MANAGE"), captor.getValue().getClaims().getClaim("authorities"));
        assertNotNull(captor.getValue().getClaims().getId());
    }
}
