package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.model.enums.Granularity;

import java.time.Duration;
import java.util.Optional;

/**
 * Maps our broker-neutral {@link Granularity} to the Saxo {@code Charts} {@code Horizon} value
 * (minutes per sample) for {@code MD3-charts-backfill}.
 *
 * <p>Saxo's {@code GET /chart/v1/charts} accepts {@code Horizon} only from a fixed minute enum
 * (SAXO_ANSWERS §3.4):</p>
 *
 * <pre>1, 5, 10, 15, 30, 60, 120, 240, 360, 480, 1440, 10080, 43200</pre>
 *
 * <p>Our {@code Granularity} enum maps cleanly onto the subset Saxo offers:</p>
 * <ul>
 *   <li>{@code ONE_MINUTE} → 1, {@code FIVE_MINUTES} → 5, {@code FIFTEEN_MINUTES} → 15,
 *       {@code THIRTY_MINUTES} → 30, {@code ONE_HOUR} → 60, {@code FOUR_HOURS} → 240,</li>
 *   <li>{@code ONE_DAY} → 1440, {@code ONE_WEEK} → 10080, {@code ONE_MONTH} → 43200
 *       (Saxo expresses a "month" horizon as 43200 minutes = 30 days).</li>
 * </ul>
 *
 * <p>The enum also carries the wall-clock {@link Duration} one sample spans, which the paginator
 * uses to walk the {@code Time} anchor backward by {@code Count × step} between pages
 * (Saxo's {@code Mode=UpTo} returns samples <em>up to</em> the anchor).</p>
 *
 * <p><b>Boundary:</b> pure value mapping, no I/O. The {@code 43200}-minute month horizon is an
 * approximation Saxo itself uses; that is a presentational artefact only — the empirical
 * earliest-available probe still measures the true history depth, it is never assumed.</p>
 */
public enum SaxoChartsHorizon {

    ONE_MINUTE(Granularity.ONE_MINUTE, 1, Duration.ofMinutes(1)),
    FIVE_MINUTES(Granularity.FIVE_MINUTES, 5, Duration.ofMinutes(5)),
    FIFTEEN_MINUTES(Granularity.FIFTEEN_MINUTES, 15, Duration.ofMinutes(15)),
    THIRTY_MINUTES(Granularity.THIRTY_MINUTES, 30, Duration.ofMinutes(30)),
    ONE_HOUR(Granularity.ONE_HOUR, 60, Duration.ofHours(1)),
    FOUR_HOURS(Granularity.FOUR_HOURS, 240, Duration.ofHours(4)),
    ONE_DAY(Granularity.ONE_DAY, 1440, Duration.ofDays(1)),
    ONE_WEEK(Granularity.ONE_WEEK, 10080, Duration.ofDays(7)),
    ONE_MONTH(Granularity.ONE_MONTH, 43200, Duration.ofDays(30));

    private final Granularity granularity;
    private final int horizonMinutes;
    private final Duration step;

    SaxoChartsHorizon(Granularity granularity, int horizonMinutes, Duration step) {
        this.granularity = granularity;
        this.horizonMinutes = horizonMinutes;
        this.step = step;
    }

    /** The Saxo {@code Horizon} query value (minutes per sample) to send on the Charts call. */
    public int horizonMinutes() {
        return horizonMinutes;
    }

    /** The wall-clock duration one sample spans — used to walk the {@code UpTo} anchor backward. */
    public Duration step() {
        return step;
    }

    /** The {@link Granularity} this horizon represents. */
    public Granularity granularity() {
        return granularity;
    }

    /**
     * Resolves the Saxo horizon for a {@link Granularity}.
     *
     * @param granularity the requested bar granularity ({@code null} defaults to daily)
     * @return the matching Saxo horizon, or empty if Saxo has no equivalent horizon
     */
    public static Optional<SaxoChartsHorizon> forGranularity(Granularity granularity) {
        Granularity g = granularity != null ? granularity : Granularity.ONE_DAY;
        for (SaxoChartsHorizon h : values()) {
            if (h.granularity == g) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }
}
