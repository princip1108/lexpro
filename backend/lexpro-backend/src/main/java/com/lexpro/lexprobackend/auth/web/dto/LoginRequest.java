package com.lexpro.lexprobackend.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "must not contain control characters")
        String username,
        @NotBlank @Size(max = 72) String password
) {
}
