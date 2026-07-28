package com.lexpro.lexprobackend.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|DISABLED", message = "must be ACTIVE or DISABLED")
        String status
) {
}
