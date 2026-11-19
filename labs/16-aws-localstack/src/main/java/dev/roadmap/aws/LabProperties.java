package dev.roadmap.aws;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Names of the AWS resources this app talks to. The app does not create them: in real life
 * Terraform or CDK owns them, and in the tests {@code LocalStackInfra} plays that role.
 */
@Validated
@ConfigurationProperties("lab")
public record LabProperties(@NotBlank String bucket, @NotBlank String ordersQueue, @NotBlank String orderEventsTopic) {
}
