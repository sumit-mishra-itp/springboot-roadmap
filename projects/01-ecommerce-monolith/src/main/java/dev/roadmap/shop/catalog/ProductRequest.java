package dev.roadmap.shop.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * Boxed Integer/Long on purpose: Jackson 3 rejects a missing primitive as unreadable JSON (a bare 400 with no
 * field), whereas a null wrapper reaches Bean Validation and comes back as a per-field "must not be null".
 */
public record ProductRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9-]+", message = "must be upper-case letters, digits or '-'") String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) @Nullable String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @PositiveOrZero Integer stock,
        @NotNull Long categoryId) {
}
