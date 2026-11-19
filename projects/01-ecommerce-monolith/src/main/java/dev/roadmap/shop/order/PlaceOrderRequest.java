package dev.roadmap.shop.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** No customer id in the body: it comes from the access token, so nobody can order on someone else's account. */
public record PlaceOrderRequest(@NotEmpty @Size(max = 50) List<@Valid @NotNull Line> lines) {

    /** Boxed types so a missing field is a per-field "must not be null", not an unreadable-JSON 400 (Jackson 3). */
    public record Line(@NotNull Long productId, @NotNull @Positive @Max(100) Integer quantity) {
    }
}
