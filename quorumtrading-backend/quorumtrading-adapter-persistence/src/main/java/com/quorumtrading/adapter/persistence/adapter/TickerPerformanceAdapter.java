package com.quorumtrading.adapter.persistence.adapter;

import com.quorumtrading.adapter.persistence.entity.PriceBarJpaEntity;
import com.quorumtrading.application.port.out.TickerPerformancePort;
import io.quarkus.arc.Unremovable;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Computes ticker performance aggregates with a single SQL query.
 * Returns start/end price, period high/low, and data point count
 * without loading all rows into memory.
 */
@ApplicationScoped
@Unremovable
public class TickerPerformanceAdapter implements TickerPerformancePort {

    private static final String AGGREGATE_SQL = """
        SELECT
            (SELECT close FROM price_bars WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC LIMIT 1) AS start_price,
            (SELECT timestamp FROM price_bars WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC LIMIT 1) AS start_date,
            (SELECT close FROM price_bars WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp DESC LIMIT 1) AS end_price,
            (SELECT timestamp FROM price_bars WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp DESC LIMIT 1) AS end_date,
            MAX(COALESCE(high, close)) AS period_high,
            MIN(COALESCE(low, close)) AS period_low,
            COUNT(*) AS data_points
        FROM price_bars
        WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3
        """;

    @Override
    public Optional<PerformanceAggregate> getPerformanceAggregate(String ticker, Instant from, Instant to) {
        if (QuarkusTransaction.isActive()) {
            return executeQuery(ticker, from, to);
        } else {
            return QuarkusTransaction.requiringNew().call(() -> executeQuery(ticker, from, to));
        }
    }

    private Optional<PerformanceAggregate> executeQuery(String ticker, Instant from, Instant to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = PriceBarJpaEntity.getEntityManager()
            .createNativeQuery(AGGREGATE_SQL)
            .setParameter(1, ticker)
            .setParameter(2, from)
            .setParameter(3, to)
            .getResultList();

        if (rows.isEmpty() || rows.get(0)[0] == null) {
            return Optional.empty();
        }

        Object[] row = rows.get(0);
        return Optional.of(new PerformanceAggregate(
            (BigDecimal) row[0],
            toInstant(row[1]),
            (BigDecimal) row[2],
            toInstant(row[3]),
            (BigDecimal) row[4],
            (BigDecimal) row[5],
            ((Number) row[6]).longValue()
        ));
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant inst) return inst;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }
}
