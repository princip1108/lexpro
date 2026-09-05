package com.lexpro.lexprobackend.system.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveModelConfigurationRequest(
        @NotBlank @Size(max=100) String displayName,
        @NotBlank @Size(max=100) String modelName,
        @NotBlank @Size(max=2048) String baseUrl,
        @Size(max=4096) String apiKey,
        boolean enableThinking,
        @Size(max=500) String remark,
        boolean enabled, boolean setActive) {
    @Override public String toString() { return "SaveModelConfigurationRequest[redacted]"; }
}
