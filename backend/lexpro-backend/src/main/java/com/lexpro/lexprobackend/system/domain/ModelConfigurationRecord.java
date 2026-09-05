package com.lexpro.lexprobackend.system.domain;

public record ModelConfigurationRecord(long configId, String displayName, String modelName, String baseUrl,
        String apiKeyCiphertext, boolean enableThinking, String remark, boolean enabled, boolean active) {
    @Override public String toString() { return "ModelConfigurationRecord[id=" + configId + "]"; }
}
