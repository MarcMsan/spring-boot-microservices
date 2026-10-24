package com.vidopos.inventory.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin(value = "0.00", message = "must be zero or positive")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal price,
        @NotNull @Min(0) @Max(1_000_000) Integer availableQuantity
) {
}
