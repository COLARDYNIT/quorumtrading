package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class MarketDataQueryParams {

    @Pattern(regexp = "^[A-Z]+$", message = "Symbol must be uppercase letters only")
    @Size(max = 20, message = "Symbol must be max 20 characters")
    public String symbol;

    @NotNull(message = "From timestamp is required")
    public Instant from;

    @NotNull(message = "To timestamp is required")
    public Instant to;

    @Size(max = 50, message = "Event type must be max 50 characters")
    public String eventType;

    @Size(max = 100, message = "Source must be max 100 characters")
    public String source;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 1000, message = "Limit must not exceed 1000")
    public Integer limit = 100;

    public MarketDataQueryParams() {}

    public MarketDataQueryParams(String symbol, Instant from, Instant to,
                                String eventType, String source, Integer limit) {
        this.symbol = symbol;
        this.from = from;
        this.to = to;
        this.eventType = eventType;
        this.source = source;
        this.limit = limit != null ? limit : 100;
    }
}
