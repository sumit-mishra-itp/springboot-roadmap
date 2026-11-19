package dev.roadmap.aws.payment;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from the Secrets Manager secret {@code /lab/payment-api}. The secret's JSON keys
 * ({@code api-key}, {@code webhook-secret}) become properties, and {@code ?prefix=payment.} on the
 * import puts them under {@code payment.*}. Never log this record: its toString prints the values.
 */
@Validated
@ConfigurationProperties("payment")
public record PaymentApiProperties(@NotBlank String apiKey, @NotBlank String webhookSecret) {
}
