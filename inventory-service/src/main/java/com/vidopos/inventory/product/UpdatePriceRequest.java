package com.vidopos.inventory.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdatePriceRequest(
        @NotNull @DecimalMin(value = "0.00", message = "must be zero or positive")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal price
) {
}
