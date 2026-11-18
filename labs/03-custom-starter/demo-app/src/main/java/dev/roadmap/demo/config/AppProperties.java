package dev.roadmap.demo.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Everything under {@code app.*}, bound once, typed, and validated at startup.
 *
 * @param owner Team that owns this service
 * @param instanceId Random id generated per start
 * @param support Support contact settings
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotBlank String owner, String instanceId, @Valid @NotNull Support support) {

    /**
     * @param onCallEmail Address paged for incidents
     * @param responseTime Target time to first response ("4h", "15m", "PT30S")
     * @param maxAttachmentSize Largest accepted attachment ("10MB")
     */
    public record Support(@NotBlank @Email String onCallEmail, Duration responseTime, DataSize maxAttachmentSize) {
    }
}
