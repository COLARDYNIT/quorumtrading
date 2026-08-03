package com.quorumtrading.model.enums;

/**
 * Time granularity for market data bars (OHLC candles).
 * Supports parsing from various string formats used by different market data providers.
 */
public enum Granularity {
    ONE_MINUTE("1Min", "every 1min"),
    FIVE_MINUTES("5Min", "every 5min"),
    FIFTEEN_MINUTES("15Min", "every 15min"),
    THIRTY_MINUTES("30Min", "every 30min"),
    ONE_HOUR("1Hour", "hourly"),
    FOUR_HOURS("4Hour", "every 4 hours"),
    ONE_DAY("1Day", "daily"),
    ONE_WEEK("1Week", "weekly"),
    ONE_MONTH("1Month", "monthly");

    private final String apiFormat;
    private final String displayName;

    Granularity(String apiFormat, String displayName) {
        this.apiFormat = apiFormat;
        this.displayName = displayName;
    }

    /**
     * Get the API format string for this granularity (e.g., "1Day", "1Hour", "5Min").
     * This format is used when communicating with market data providers.
     */
    public String getApiFormat() {
        return apiFormat;
    }

    /**
     * Get the human-readable display name (e.g., "daily", "hourly", "every 5min").
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parse a string to a Granularity enum value.
     * Supports various formats: "1Day", "daily", "1d", "1D", etc.
     *
     * @param value the string value to parse (case-insensitive)
     * @return the corresponding Granularity enum value
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    public static Granularity fromString(String value) {
        if (value == null || value.isBlank()) {
            return ONE_DAY; // Default to daily
        }

        String normalized = value.trim().toLowerCase();

        // Try exact match on API format first
        for (Granularity g : values()) {
            if (g.apiFormat.equalsIgnoreCase(value)) {
                return g;
            }
        }

        // Try common aliases
        return switch (normalized) {
            // 1-minute aliases
            case "1min", "1m", "1minute" -> ONE_MINUTE;

            // 5-minute aliases
            case "5min", "5m", "5minutes" -> FIVE_MINUTES;

            // 15-minute aliases
            case "15min", "15m", "15minutes" -> FIFTEEN_MINUTES;

            // 30-minute aliases
            case "30min", "30m", "30minutes" -> THIRTY_MINUTES;

            // 1-hour aliases
            case "1hour", "1h", "60min", "hourly", "hour" -> ONE_HOUR;

            // 4-hour aliases
            case "4hour", "4h", "4hours" -> FOUR_HOURS;

            // 1-day aliases
            case "1day", "1d", "daily", "day" -> ONE_DAY;

            // 1-week aliases
            case "1week", "1w", "weekly", "week" -> ONE_WEEK;

            // 1-month aliases
            case "1month", "1mo", "monthly", "month" -> ONE_MONTH;

            default -> throw new IllegalArgumentException(
                "Invalid granularity: '" + value + "'. " +
                "Supported values: 1Min, 5Min, 15Min, 30Min, 1Hour, 4Hour, 1Day, 1Week, 1Month " +
                "(or aliases like '1m', '5m', '1h', '1d', 'daily', 'hourly', etc.)"
            );
        };
    }

    /**
     * Check if this granularity is intraday (less than 1 day).
     */
    public boolean isIntraday() {
        return this == ONE_MINUTE || this == FIVE_MINUTES || this == FIFTEEN_MINUTES ||
               this == THIRTY_MINUTES || this == ONE_HOUR || this == FOUR_HOURS;
    }

    /**
     * Check if this granularity is daily or greater.
     */
    public boolean isDailyOrGreater() {
        return this == ONE_DAY || this == ONE_WEEK || this == ONE_MONTH;
    }
}
