package com.lexpro.lexprobackend.auth.password;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordService {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final PasswordEncoder passwordEncoder;

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String encode(String rawPassword) {
        validate(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null
                && passwordHash != null
                && rawPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES
                && passwordEncoder.matches(rawPassword, passwordHash);
    }

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must contain at least 12 characters");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        if (rawPassword.chars().noneMatch(Character::isUpperCase)
                || rawPassword.chars().noneMatch(Character::isLowerCase)
                || rawPassword.chars().noneMatch(Character::isDigit)
                || rawPassword.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException(
                    "Password must include uppercase, lowercase, number, and special characters"
            );
        }
    }
}
