package com.lexpro.lexprobackend.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(max = 72) String password,
        @NotBlank @Size(max = 100) String realName,
        @NotNull @Positive Long organizationId,
        @NotNull @Positive Long roleId
) {
}
