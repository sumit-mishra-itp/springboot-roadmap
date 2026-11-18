package dev.roadmap.observability.orders;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** channel and tier become tag values: a null would fail tag creation, so reject it at the boundary. */
public record OrderRequest(@NotBlank String channel, @NotBlank String tier, @NotNull @Positive BigDecimal amount) {
}
