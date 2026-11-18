package dev.roadmap.reactive.price;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PriceRequest(
        @NotNull @Pattern(regexp = "[A-Z]{1,10}") String symbol,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount) {
}
