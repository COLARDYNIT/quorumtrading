package com.quorumtrading.model.orchestration;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-readable duration strings into {@link Duration} objects.
 *
 * <p>Supported formats:</p>
 * <ul>
 *   <li>{@code 30s} — 30 seconds</li>
 *   <li>{@code 5m} or {@code 5min} — 5 minutes</li>
 *   <li>{@code 1h} — 1 hour</li>
 *   <li>{@code 1d} — 1 day (24 hours)</li>
 *   <li>{@code 1w} — 1 week (7 days)</li>
 * </ul>
 */
public final class DurationParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(s|min|m|h|d|w)$");

    private DurationParser() {}

    /**
     * Parse a human-readable duration string into seconds.
     *
     * @param input duration string (e.g., "1h", "30s", "5m", "1d", "1w")
     * @return duration in seconds
     * @throws IllegalArgumentException if the format is invalid
     */
    public static int parseToSeconds(String input) {
        return (int) parse(input).toSeconds();
    }

    /**
     * Parse a human-readable duration string into a {@link Duration}.
     *
     * @param input duration string (e.g., "1h", "30s", "5m", "1d", "1w")
     * @return parsed Duration
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration string cannot be null or blank");
        }

        String trimmed = input.trim().toLowerCase();
        Matcher matcher = DURATION_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid duration format: '" + input + "'. Expected: <number><unit> where unit is s, m, min, h, d, or w (e.g., '1h', '30s', '5m', '1d', '1w')"
            );
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        if (value <= 0) {
            throw new IllegalArgumentException("Duration value must be positive, got: " + value);
        }

        return switch (unit) {
            case "s" -> Duration.ofSeconds(value);
            case "m", "min" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            case "w" -> Duration.ofDays(value * 7);
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }
}
