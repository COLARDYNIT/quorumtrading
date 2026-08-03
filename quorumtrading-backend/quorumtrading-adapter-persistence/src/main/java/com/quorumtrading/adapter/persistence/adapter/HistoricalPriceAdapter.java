package com.quorumtrading.adapter.persistence.adapter;

import com.quorumtrading.adapter.persistence.entity.PriceBarJpaEntity;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.PriceCoverage;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.arc.Unremovable;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Adapter to fetch historical prices from price_bars table.
 *
 * <p><b>Hexagonal Architecture:</b> Persistence adapter (driven)</p>
 * <p>Queries price_bars table with native OHLCV columns (no JSONB parsing).</p>
 */
@ApplicationScoped
@Unremovable
public class HistoricalPriceAdapter implements HistoricalPricePort {

    private static final Logger log = Logger.getLogger(HistoricalPriceAdapter.class);

    @Inject
    EntityManager entityManager;

    /**
     * Fetches historical price data for the given ticker.
     *
     * <p>Handles both transactional and non-transactional contexts:
     * <ul>
     *   <li>If a transaction is already active (e.g., in tests or from REST calls), uses it directly</li>
     *   <li>If no transaction exists (e.g., from async ManagedExecutor threads), creates a new one</li>
     * </ul>
     * </p>
     */
    @Override
    public List<PriceBar> getHistoricalPrices(Symbol ticker, int days) {
        return getHistoricalPrices(ticker, days, Instant.now());
    }

    @Override
    public List<PriceBar> getHistoricalPrices(Symbol ticker, int days, Instant asOf) {
        if (QuarkusTransaction.isActive()) {
            return executeQuery(ticker, days, asOf);
        } else {
            return QuarkusTransaction.requiringNew().call(() -> executeQuery(ticker, days, asOf));
        }
    }

    @Override
    public List<PriceBar> getHistoricalPrices(Symbol ticker, Instant from, Instant to) {
        if (QuarkusTransaction.isActive()) {
            return executeRangeQuery(ticker, from, to);
        } else {
            return QuarkusTransaction.requiringNew().call(() -> executeRangeQuery(ticker, from, to));
        }
    }

    private List<PriceBar> executeQuery(Symbol ticker, int days, Instant asOf) {
        // Use a generous calendar-day lookback: 1.5x to cover weekends/holidays.
        Instant cutoff = asOf.minus((long) (days * 1.5), ChronoUnit.DAYS);

        // Aggregate to daily resolution using DISTINCT ON to collapse intraday bars.
        // Without this, a LIMIT of `days` rows on a ticker with 5-min bars would
        // return only a few hours of data instead of the requested trading days.
        String sql = "SELECT DISTINCT ON (DATE_TRUNC('day', timestamp)) "
            + "timestamp, open, high, low, close, volume "
            + "FROM price_bars "
            // Match the instrument's asset class too: a ticker can exist as both an equity and
            // a crypto, and matching on symbol alone let one instrument's bars feed the other's
            // indicators. Rows written before V127 carry no type and are always accepted, or
            // every pre-existing simulation would lose its price history.
            //
            // The symbol parameter is the CANONICAL form, never the bare root — the same class
            // of collision one venue over. AZN is AstraZeneca's New York receipt in dollars and
            // AZN:XLON its London ordinary share in pounds; looking the latter up by its root
            // fed a London-labelled backtest the New York bars and reported it as a success.
            + "WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3 "
            + "AND (investment_type IS NULL OR investment_type = ?5) "
            + "ORDER BY DATE_TRUNC('day', timestamp) DESC, timestamp DESC "
            + "LIMIT ?4";

        var query = entityManager.createNativeQuery(sql);
        query.setParameter(1, ticker.canonical());
        query.setParameter(2, cutoff);
        query.setParameter(3, asOf);
        query.setParameter(4, days);
        query.setParameter(5, ticker.type().name());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        log.debugf("Found %d daily price bars for %s (requested last %d trading days as-of %s)",
            rows.size(), ticker.canonical(), (Object) days, asOf);

        // Rows are DESC, reverse to ascending order (most recent last) as callers expect
        List<Object[]> ascending = new ArrayList<>(rows);
        Collections.reverse(ascending);

        return ascending.stream()
            .map(row -> {
                Instant ts;
                if (row[0] instanceof java.time.OffsetDateTime odt) {
                    ts = odt.toInstant();
                } else if (row[0] instanceof java.sql.Timestamp sqlTs) {
                    ts = sqlTs.toInstant();
                } else {
                    ts = (Instant) row[0];
                }
                return new PriceBar(
                    ts,
                    (BigDecimal) row[1],
                    (BigDecimal) row[2],
                    (BigDecimal) row[3],
                    (BigDecimal) row[4],
                    (BigDecimal) row[5]
                );
            })
            .toList();
    }

    @Override
    public List<PriceBar> getHistoricalPrices(Symbol ticker, Instant from, Instant to, String resolution) {
        if (resolution == null || resolution.isEmpty()) {
            return getHistoricalPrices(ticker, from, to);
        }
        if (QuarkusTransaction.isActive()) {
            return executeResolutionQuery(ticker, from, to, resolution);
        } else {
            return QuarkusTransaction.requiringNew().call(() -> executeResolutionQuery(ticker, from, to, resolution));
        }
    }

    /**
     * Maps resolution string to PostgreSQL DATE_TRUNC interval.
     */
    private static String resolutionToDateTrunc(String resolution) {
        return switch (resolution) {
            case "1w" -> "week";
            case "1d" -> "day";
            case "1h" -> "hour";
            case "5m" -> "minute"; // no truncation benefit at 5m, but consistent
            default -> "day";
        };
    }

    private List<PriceBar> executeResolutionQuery(Symbol ticker, Instant from, Instant to, String resolution) {
        String truncInterval = resolutionToDateTrunc(resolution);

        // DISTINCT ON picks the first row per (symbol, time_bucket) group, so the trailing
        // ORDER BY decides WHICH bar in the bucket survives. It must be DESC: the surviving row
        // is presented downstream as that period's close, and taking the oldest bar made the
        // "close" of an intraday day roughly its opening price. Every indicator built on this
        // series — RSI, SMA, Bollinger — was reading opens labelled as closes, which is how an
        // RSI-14 was observed rising on a down day.
        String sql = "SELECT DISTINCT ON (symbol, DATE_TRUNC('" + truncInterval + "', timestamp)) "
            + "timestamp, open, high, low, close, volume "
            + "FROM price_bars "
            + "WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3 "
            + "ORDER BY symbol, DATE_TRUNC('" + truncInterval + "', timestamp), timestamp DESC";

        var query = entityManager.createNativeQuery(sql);
        query.setParameter(1, ticker.canonical());
        query.setParameter(2, from);
        query.setParameter(3, to);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        log.debugf("Found %d price bars for %s between %s and %s at resolution %s",
            rows.size(), ticker.canonical(), from, to, resolution);

        return rows.stream()
            .map(row -> {
                Instant ts;
                if (row[0] instanceof java.time.OffsetDateTime odt) {
                    ts = odt.toInstant();
                } else if (row[0] instanceof java.sql.Timestamp sqlTs) {
                    ts = sqlTs.toInstant();
                } else {
                    ts = (Instant) row[0];
                }
                return new PriceBar(
                    ts,
                    (BigDecimal) row[1], // open
                    (BigDecimal) row[2], // high
                    (BigDecimal) row[3], // low
                    (BigDecimal) row[4], // close
                    (BigDecimal) row[5]  // volume
                );
            })
            .toList();
    }

    @Override
    public PriceCoverage getCoverage(Symbol ticker) {
        if (QuarkusTransaction.isActive()) {
            return executeCoverageQuery(ticker);
        } else {
            return QuarkusTransaction.requiringNew().call(() -> executeCoverageQuery(ticker));
        }
    }

    @Override
    public PriceCoverage getCoverage(Symbol ticker, java.time.LocalDate from, java.time.LocalDate to) {
        if (QuarkusTransaction.isActive()) {
            return executeWindowedCoverageQuery(ticker, from, to);
        } else {
            return QuarkusTransaction.requiringNew().call(() -> executeWindowedCoverageQuery(ticker, from, to));
        }
    }

    private PriceCoverage executeWindowedCoverageQuery(Symbol ticker, java.time.LocalDate from,
                                                       java.time.LocalDate to) {
        PriceCoverage overall = executeCoverageQuery(ticker);
        // The window is computed from the days actually stored rather than from a count, because
        // "1522 days between 2016 and 2026" and "1522 contiguous days" are indistinguishable from
        // a count and lead to opposite conclusions about a backtest.
        java.time.Instant start = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        java.time.Instant end = to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        String sql = "SELECT DISTINCT CAST(timestamp AT TIME ZONE 'UTC' AS date) "
            + "FROM price_bars WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp < ?3";
        var query = entityManager.createNativeQuery(sql);
        query.setParameter(1, ticker.canonical());
        query.setParameter(2, start);
        query.setParameter(3, end);

        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        Set<java.time.LocalDate> storedDays = rows.stream()
            .map(HistoricalPriceAdapter::toLocalDate)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

        return overall.withWindow(
            PriceCoverage.buildWindow(from, to, ticker.type(), storedDays));
    }

    private static java.time.LocalDate toLocalDate(Object value) {
        if (value instanceof java.time.LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.time.temporal.TemporalAccessor temporal) {
            return java.time.LocalDate.from(temporal);
        }
        return null;
    }

    private PriceCoverage executeCoverageQuery(Symbol ticker) {
        String sql = "SELECT COUNT(*), COUNT(DISTINCT DATE_TRUNC('day', timestamp)), "
            + "MIN(timestamp), MAX(timestamp), STRING_AGG(DISTINCT source, ',') "
            + "FROM price_bars WHERE symbol = ?1";

        var query = entityManager.createNativeQuery(sql);
        query.setParameter(1, ticker.canonical());

        Object[] row = (Object[]) query.getSingleResult();
        long barCount = ((Number) row[0]).longValue();
        if (barCount == 0) {
            return PriceCoverage.empty(ticker.canonical());
        }
        long distinctDays = ((Number) row[1]).longValue();
        Instant first = toInstant(row[2]);
        Instant last = toInstant(row[3]);
        List<String> sources = row[4] == null ? List.of() : List.of(((String) row[4]).split(","));
        return new PriceCoverage(ticker.canonical(), barCount, distinctDays, first, last, sources);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.sql.Timestamp sqlTs) {
            return sqlTs.toInstant();
        }
        return (Instant) value;
    }

    private List<PriceBar> executeRangeQuery(Symbol ticker, Instant from, Instant to) {
        List<PriceBarJpaEntity> priceBars = PriceBarJpaEntity.find(
            "symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC",
            ticker.canonical(), from, to
        ).list();

        log.debugf("Found %d price bars for %s between %s and %s",
            priceBars.size(), ticker.canonical(), from, to);

        return mapToValueObjects(priceBars);
    }

    private List<PriceBar> mapToValueObjects(List<PriceBarJpaEntity> entities) {
        return entities.stream()
            .map(entity -> new PriceBar(
                entity.timestamp,
                entity.open,
                entity.high,
                entity.low,
                entity.close,
                entity.volume
            ))
            .toList();
    }
}
