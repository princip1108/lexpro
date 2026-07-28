package com.lexpro.lexprobackend.auth.password;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordServiceTests {

    private final PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder(4));

    @Test
    void shouldEncodeAndVerifyStrongPassword() {
        String rawPassword = "StrongPass1!";

        String hash = passwordService.encode(rawPassword);

        assertNotEquals(rawPassword, hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(passwordService.matches(rawPassword, hash));
        assertFalse(passwordService.matches("WrongPass1!", hash));
        assertFalse(passwordService.matches("密".repeat(30), hash));
    }

    @Test
    void shouldRejectWeakOrOverlongPasswords() {
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode("short"));
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode("alllowercase1!"));
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode("ALLUPPERCASE1!"));
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode("NoNumberHere!"));
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode("NoSpecial1234"));
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode("Aa1!" + "x".repeat(69)));
    }
}
