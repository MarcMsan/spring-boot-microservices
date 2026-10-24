package com.vidopos.order.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotBlank @Email @Size(max = 254) String customerEmail,
        @NotEmpty @Size(max = 20) @Valid List<Item> items
) {
    public record Item(
            @NotNull UUID productId,
            @NotNull @Min(1) @Max(1000) Integer quantity
    ) {
    }
}
