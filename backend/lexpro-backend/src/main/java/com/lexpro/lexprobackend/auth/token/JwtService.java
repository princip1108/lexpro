package com.lexpro.lexprobackend.auth.token;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this(jwtEncoder, properties, Clock.systemUTC());
    }

    JwtService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(UserAccount account, List<String> permissions) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + account.getRoleCode());
        authorities.addAll(permissions);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(account.getUserId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("username", account.getUsername())
                .claim("realName", account.getRealName())
                .claim("authorities", List.copyOf(authorities))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
