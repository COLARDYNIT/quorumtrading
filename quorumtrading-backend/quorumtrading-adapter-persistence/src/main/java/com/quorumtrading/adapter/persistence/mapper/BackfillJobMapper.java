package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.BackfillJobJpaEntity;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TimeRange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mapper between BackfillJob domain entity and BackfillJobJpaEntity.
 *
 * Handles serialization/deserialization of complex fields:
 * - Symbols: Set<Symbol> ↔ JSONB array of strings
 * - DataTypes: Set<EventType> ↔ JSONB array of enum names
 * - Gaps: List<MarketDataGap> ↔ flattened date range + symbol/type sets
 *
 * ARCHITECTURAL NOTE:
 * The domain model uses MarketDataGap records with full TimeRange details,
 * but the JPA entity stores a flattened representation (date_from/date_to + symbol/type sets)
 * for simpler querying. The mapper reconstructs gaps during toDomain conversion.
 *
 * MARKET-WIDE DATA:
 * Market-wide gaps (NEWS, SOCIAL) have null symbols in the domain model.
 * We use "MARKET" as a sentinel value when persisting to distinguish these
 * from symbol-specific gaps.
 */
@ApplicationScoped
public class BackfillJobMapper {

    /**
     * Sentinel value used to represent market-wide gaps (null symbol) in persistence.
     */
    private static final String MARKET_WIDE_SENTINEL = "MARKET";

    @Inject
    ObjectMapper objectMapper;

    /**
     * Convert domain BackfillJob to JPA entity.
     *
     * Extracts symbols, data types, and date range from the gaps list.
     * Serializes symbols and data types as JSON arrays for JSONB storage.
     *
     * @param domain the domain BackfillJob
     * @return the JPA entity
     * @throws RuntimeException if JSON serialization fails
     */
    public BackfillJobJpaEntity toEntity(BackfillJob domain) {
        BackfillJobJpaEntity entity = new BackfillJobJpaEntity();
        entity.setId(domain.getId());
        entity.setStatus(domain.getStatus().name());
        entity.setProgressPercentage(domain.getProgressPercentage());
        entity.setCreatedAt(Instant.now());
        entity.setStartedAt(domain.getStartedAt());
        entity.setCompletedAt(domain.getCompletedAt());
        entity.setErrorMessage(domain.getErrorMessage());
        entity.setTotalRecordsExpected(domain.getTotalRecordsExpected());
        entity.setRecordsProcessed(domain.getRecordsProcessed());

        // Extract unique symbols from gaps (use sentinel for market-wide gaps)
        Set<String> symbols = domain.getGaps().stream()
            .map(gap -> gap.symbol() != null ? gap.symbol().ticker() : MARKET_WIDE_SENTINEL)
            .collect(Collectors.toSet());

        // Extract unique data types from gaps
        Set<String> dataTypes = domain.getGaps().stream()
            .map(gap -> gap.dataType().name())
            .collect(Collectors.toSet());

        // Find min/max dates from gaps
        LocalDate minDate = domain.getGaps().stream()
            .map(gap -> gap.period().getStart())
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now(ZoneOffset.UTC));

        LocalDate maxDate = domain.getGaps().stream()
            .map(gap -> gap.period().getEnd())
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now(ZoneOffset.UTC));

        // Serialize to JSON
        try {
            entity.setSymbols(objectMapper.writeValueAsString(symbols));
            entity.setDataTypes(objectMapper.writeValueAsString(dataTypes));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize symbols/dataTypes to JSON", e);
        }

        entity.setDateFrom(minDate);
        entity.setDateTo(maxDate);

        // Set linked simulation ID if present
        domain.getLinkedSimulationId()
            .map(SimulationId::value)
            .ifPresent(entity::setLinkedSimulationId);

        entity.setRetryAfter(domain.getRetryAfter());
        entity.setGranularity(domain.getGranularity().name());

        return entity;
    }

    /**
     * Convert JPA entity to domain BackfillJob.
     *
     * Reconstructs gaps from the flattened representation by creating a gap
     * for each symbol/type combination over the full date range.
     *
     * NOTE: This is a simplified reconstruction. The original gaps may have
     * had different date ranges per symbol/type, but we recreate them with
     * a uniform range. This is acceptable because:
     * 1. The backfill processor will check existing data before fetching
     * 2. The date range represents the overall job scope
     *
     * Uses BackfillJob.reconstitute() to restore all persisted state
     * (status, progress, timestamps) for proper round-trip persistence.
     *
     * @param entity the JPA entity
     * @return the domain BackfillJob (with fully reconstructed state)
     * @throws RuntimeException if JSON deserialization fails
     */
    public BackfillJob toDomain(BackfillJobJpaEntity entity) {
        // Deserialize symbols and data types from JSON
        Set<String> symbols;
        Set<String> dataTypes;

        try {
            symbols = objectMapper.readValue(entity.getSymbols(), new TypeReference<Set<String>>() {});
            dataTypes = objectMapper.readValue(entity.getDataTypes(), new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize symbols/dataTypes from JSON", e);
        }

        // Reconstruct gaps for each symbol/type combination
        List<MarketDataGap> gaps = new ArrayList<>();
        TimeRange period = TimeRange.of(entity.getDateFrom(), entity.getDateTo());

        for (String symbol : symbols) {
            for (String dataType : dataTypes) {
                EventType eventType = EventType.valueOf(dataType);
                // Use null symbol for market-wide gaps (sentinel value).
                // Re-detect the asset class from the ticker on reload — the flattened
                // JSON only stores the ticker string, so hardcoding STOCK here would
                // make every crypto gap fetch via the equity (weekday-only) path and
                // silently drop crypto weekends. fromTicker matches request-time parsing.
                Symbol domainSymbol = MARKET_WIDE_SENTINEL.equals(symbol)
                    ? null
                    : new Symbol(symbol, InvestmentType.fromTicker(symbol));

                // Skip invalid combinations (e.g., MARKET + PRICE which requires a symbol)
                if (domainSymbol == null && eventType == EventType.PRICE) {
                    continue;
                }

                gaps.add(new MarketDataGap(
                    domainSymbol,
                    eventType,
                    period
                ));
            }
        }

        // Parse linked simulation ID if present
        SimulationId linkedSimulationId = entity.getLinkedSimulationId() != null
            ? new SimulationId(entity.getLinkedSimulationId())
            : null;

        // Parse granularity (default to ONE_DAY for older jobs without granularity)
        Granularity granularity = entity.getGranularity() != null
            ? Granularity.valueOf(entity.getGranularity())
            : Granularity.ONE_DAY;

        // Reconstitute BackfillJob with all persisted state
        return BackfillJob.reconstitute(
            entity.getId(),
            gaps,
            BackfillStatus.valueOf(entity.getStatus()),
            entity.getProgressPercentage(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getErrorMessage(),
            entity.getTotalRecordsExpected(),
            entity.getRecordsProcessed(),
            linkedSimulationId,
            entity.getRetryAfter(),
            granularity
        );
    }
}
