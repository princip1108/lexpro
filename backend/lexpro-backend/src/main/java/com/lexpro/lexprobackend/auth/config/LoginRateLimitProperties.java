package com.lexpro.lexprobackend.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@Component
@ConfigurationProperties(prefix = "lexpro.auth.rate-limit")
public class LoginRateLimitProperties {

    private boolean enabled = true;
    @Min(1)
    private int usernameMaxAttempts = 5;
    @Min(1)
    private int addressMaxAttempts = 100;
    @NotNull
    private Duration window = Duration.ofMinutes(5);
    @NotNull
    private Duration blockDuration = Duration.ofMinutes(15);
    @Min(100)
    private int maxTrackedEntries = 10000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getUsernameMaxAttempts() { return usernameMaxAttempts; }
    public void setUsernameMaxAttempts(int usernameMaxAttempts) { this.usernameMaxAttempts = usernameMaxAttempts; }
    public int getAddressMaxAttempts() { return addressMaxAttempts; }
    public void setAddressMaxAttempts(int addressMaxAttempts) { this.addressMaxAttempts = addressMaxAttempts; }
    public Duration getWindow() { return window; }
    public void setWindow(Duration window) { this.window = window; }
    public Duration getBlockDuration() { return blockDuration; }
    public void setBlockDuration(Duration blockDuration) { this.blockDuration = blockDuration; }
    public int getMaxTrackedEntries() { return maxTrackedEntries; }
    public void setMaxTrackedEntries(int maxTrackedEntries) { this.maxTrackedEntries = maxTrackedEntries; }

    @AssertTrue(message = "rate-limit durations must be positive")
    public boolean isDurationConfigurationValid() {
        return window != null && !window.isZero() && !window.isNegative()
                && blockDuration != null && !blockDuration.isZero() && !blockDuration.isNegative();
    }
}
