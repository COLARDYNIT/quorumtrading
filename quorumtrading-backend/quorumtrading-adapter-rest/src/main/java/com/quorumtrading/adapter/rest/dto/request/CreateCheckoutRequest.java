package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO for creating a payment checkout session.
 */
public record CreateCheckoutRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum top-up amount is $1.00")
        BigDecimal amount,

        @NotBlank(message = "Success URL is required")
        String successUrl,

        @NotBlank(message = "Cancel URL is required")
        String cancelUrl
) {}
