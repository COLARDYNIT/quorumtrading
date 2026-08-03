package com.quorumtrading.adapter.persistence.entity;

import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * TDD tests for MarketResearchJpaEntity domain mapping.
 * Following hexagonal architecture: JPA entity must be separate from domain entity.
 */
class MarketResearchJpaEntityTest {

    private static final UserId TEST_OWNER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void fromDomain_createsCorrectJpaEntity() {
        // Given: A domain entity with all fields populated
        var symbols = Set.of(
            new Symbol("BTC", InvestmentType.CRYPTO),
            new Symbol("ETH", InvestmentType.CRYPTO)
        );
        var interval = new ResearchInterval(7, ResearchInterval.Unit.DAYS);
        var backtestConfig = new BacktestConfiguration(
            Duration.ofDays(30),
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-31T00:00:00Z")
        );

        var domain = new MarketResearch(
            "Weekly Crypto Research",
            "# Research BTC and ETH\nLook for trading opportunities",
            symbols,
            interval,
            true,
            backtestConfig,
            TEST_OWNER
        );

        // When: Converting to JPA entity
        var jpaEntity = MarketResearchJpaEntity.fromDomain(domain);

        // Then: All fields are correctly mapped
        assertEquals(domain.getId(), jpaEntity.getId());
        assertEquals("Weekly Crypto Research", jpaEntity.getName());
        assertEquals("# Research BTC and ETH\nLook for trading opportunities", jpaEntity.getDescription());
        assertEquals("BTC,ETH", jpaEntity.getTargetAssets()); // comma-separated tickers
        assertEquals(7, jpaEntity.getIntervalAmount());
        assertEquals(ResearchInterval.Unit.DAYS.name(), jpaEntity.getIntervalUnit());
        assertEquals(ResearchStatus.ACTIVE.name(), jpaEntity.getStatus());
        assertTrue(jpaEntity.isAutoBacktest());
        assertEquals(30 * 24 * 60 * 60L, jpaEntity.getBacktestLengthSeconds()); // 30 days in seconds
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), jpaEntity.getBacktestPeriodStart());
        assertEquals(Instant.parse("2024-01-31T00:00:00Z"), jpaEntity.getBacktestPeriodEnd());
        assertNotNull(jpaEntity.getCreatedAt());
        assertNotNull(jpaEntity.getNextScheduledRun());
        assertNull(jpaEntity.getLastExecutedAt());
    }

    @Test
    void fromDomain_withNullableFields() {
        // Given: Domain entity with no backtest configuration
        var symbols = Set.of(new Symbol("AAPL", InvestmentType.STOCK));
        var interval = new ResearchInterval(1, ResearchInterval.Unit.WEEKS);

        var domain = new MarketResearch(
            "Simple Research",
            "Basic research instructions",
            symbols,
            interval,
            false,
            null,
            TEST_OWNER
        );

        // When: Converting to JPA entity
        var jpaEntity = MarketResearchJpaEntity.fromDomain(domain);

        // Then: Nullable fields are null
        assertEquals("Simple Research", jpaEntity.getName());
        assertFalse(jpaEntity.isAutoBacktest());
        assertNull(jpaEntity.getBacktestLengthSeconds());
        assertNull(jpaEntity.getBacktestPeriodStart());
        assertNull(jpaEntity.getBacktestPeriodEnd());
    }

    @Test
    void toDomain_reconstructsDomainEntity() {
        // Given: A JPA entity with all fields populated
        var jpaEntity = new MarketResearchJpaEntity();
        jpaEntity.setId(java.util.UUID.randomUUID());
        jpaEntity.setName("Daily Analysis");
        jpaEntity.setDescription("Daily market analysis");
        jpaEntity.setTargetAssets("BTC,ETH,DOGE");
        jpaEntity.setIntervalAmount(1);
        jpaEntity.setIntervalUnit(ResearchInterval.Unit.DAYS.name());
        jpaEntity.setStatus(ResearchStatus.PAUSED.name());
        jpaEntity.setAutoBacktest(true);
        jpaEntity.setBacktestLengthSeconds(7 * 24 * 60 * 60L); // 7 days
        jpaEntity.setBacktestPeriodStart(Instant.parse("2024-02-01T00:00:00Z"));
        jpaEntity.setBacktestPeriodEnd(Instant.parse("2024-02-07T00:00:00Z"));
        jpaEntity.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        jpaEntity.setLastExecutedAt(Instant.parse("2024-01-02T00:00:00Z"));
        jpaEntity.setNextScheduledRun(Instant.parse("2024-01-03T00:00:00Z"));

        // When: Converting to domain entity
        var domain = jpaEntity.toDomain();

        // Then: Domain entity is correctly reconstructed
        assertEquals(jpaEntity.getId(), domain.getId());
        assertEquals("Daily Analysis", domain.getName());
        assertEquals("Daily market analysis", domain.getMarkdownInstructions());
        assertEquals(3, domain.getTargetAssets().size());
        // Note: Using deprecated constructor which infers type from ticker
        assertTrue(domain.getTargetAssets().stream().anyMatch(s -> s.ticker().equals("BTC")));
        assertTrue(domain.getTargetAssets().stream().anyMatch(s -> s.ticker().equals("ETH")));
        assertTrue(domain.getTargetAssets().stream().anyMatch(s -> s.ticker().equals("DOGE")));
        assertEquals(1, domain.getInterval().amount());
        assertEquals(ResearchInterval.Unit.DAYS, domain.getInterval().unit());
        assertEquals(ResearchStatus.PAUSED, domain.getStatus());
        assertTrue(domain.isEnableAutoBacktest());
        assertNotNull(domain.getBacktestConfig());
        assertEquals(Duration.ofDays(7), domain.getBacktestConfig().backtestLength());
        assertEquals(Instant.parse("2024-02-01T00:00:00Z"), domain.getBacktestConfig().periodStart());
        assertEquals(Instant.parse("2024-02-07T00:00:00Z"), domain.getBacktestConfig().periodEnd());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), domain.getCreatedAt());
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), domain.getLastExecutedAt());
        assertEquals(Instant.parse("2024-01-03T00:00:00Z"), domain.getNextScheduledRun());
    }

    @Test
    void toDomain_withNullableFields() {
        // Given: JPA entity without backtest configuration
        var jpaEntity = new MarketResearchJpaEntity();
        jpaEntity.setId(java.util.UUID.randomUUID());
        jpaEntity.setName("Simple");
        jpaEntity.setDescription("Simple research");
        jpaEntity.setTargetAssets("BTC");
        jpaEntity.setIntervalAmount(2);
        jpaEntity.setIntervalUnit(ResearchInterval.Unit.MONTHS.name());
        jpaEntity.setStatus(ResearchStatus.ACTIVE.name());
        jpaEntity.setAutoBacktest(false);
        jpaEntity.setCreatedAt(Instant.now());
        jpaEntity.setNextScheduledRun(Instant.now().plusSeconds(60 * 24 * 60 * 60)); // 60 days

        // When: Converting to domain
        var domain = jpaEntity.toDomain();

        // Then: Nullable fields handled correctly
        assertEquals("Simple", domain.getName());
        assertFalse(domain.isEnableAutoBacktest());
        assertNull(domain.getBacktestConfig());
        assertNull(domain.getLastExecutedAt());
    }

    @Test
    void roundTrip_preservesAllData() {
        // Given: A complete domain entity
        var symbols = Set.of(
            new Symbol("SPY", InvestmentType.STOCK),
            new Symbol("QQQ", InvestmentType.STOCK)
        );
        var interval = new ResearchInterval(2, ResearchInterval.Unit.WEEKS);
        var backtestConfig = new BacktestConfiguration(
            Duration.ofDays(14),
            Instant.parse("2024-03-01T00:00:00Z"),
            Instant.parse("2024-03-15T00:00:00Z")
        );

        var originalDomain = new MarketResearch(
            "Bi-weekly ETF Research",
            "# ETF Analysis\nAnalyze SPY and QQQ trends",
            symbols,
            interval,
            true,
            backtestConfig,
            TEST_OWNER
        );

        // When: Converting to JPA and back to domain
        var jpaEntity = MarketResearchJpaEntity.fromDomain(originalDomain);
        var reconstructedDomain = jpaEntity.toDomain();

        // Then: All data is preserved
        assertEquals(originalDomain.getId(), reconstructedDomain.getId());
        assertEquals(originalDomain.getName(), reconstructedDomain.getName());
        assertEquals(originalDomain.getMarkdownInstructions(), reconstructedDomain.getMarkdownInstructions());
        // Note: Symbol equality compares both ticker and type, roundtrip preserves ticker (type inferred)
        assertEquals(originalDomain.getTargetAssets().size(), reconstructedDomain.getTargetAssets().size());
        for (Symbol original : originalDomain.getTargetAssets()) {
            assertTrue(reconstructedDomain.getTargetAssets().stream()
                .anyMatch(s -> s.ticker().equals(original.ticker())));
        }
        assertEquals(originalDomain.getInterval(), reconstructedDomain.getInterval());
        assertEquals(originalDomain.getStatus(), reconstructedDomain.getStatus());
        assertEquals(originalDomain.isEnableAutoBacktest(), reconstructedDomain.isEnableAutoBacktest());
        assertEquals(originalDomain.getBacktestConfig(), reconstructedDomain.getBacktestConfig());
        // Note: timestamps may differ slightly, so we just check they're not null
        assertNotNull(reconstructedDomain.getCreatedAt());
        assertNotNull(reconstructedDomain.getNextScheduledRun());
    }

    @Test
    void roundTrip_withMinimalData() {
        // Given: Minimal domain entity
        var symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        var interval = new ResearchInterval(1, ResearchInterval.Unit.DAYS);

        var originalDomain = new MarketResearch(
            "Minimal",
            "Instructions",
            symbols,
            interval,
            false,
            null,
            TEST_OWNER
        );

        // When: Round-trip conversion
        var jpaEntity = MarketResearchJpaEntity.fromDomain(originalDomain);
        var reconstructedDomain = jpaEntity.toDomain();

        // Then: Data preserved
        assertEquals(originalDomain.getId(), reconstructedDomain.getId());
        assertEquals(originalDomain.getName(), reconstructedDomain.getName());
        assertEquals(originalDomain.getMarkdownInstructions(), reconstructedDomain.getMarkdownInstructions());
        // Check ticker is preserved
        assertEquals(originalDomain.getTargetAssets().size(), reconstructedDomain.getTargetAssets().size());
        for (Symbol original : originalDomain.getTargetAssets()) {
            assertTrue(reconstructedDomain.getTargetAssets().stream()
                .anyMatch(s -> s.ticker().equals(original.ticker())));
        }
        assertEquals(originalDomain.getInterval(), reconstructedDomain.getInterval());
        assertFalse(reconstructedDomain.isEnableAutoBacktest());
        assertNull(reconstructedDomain.getBacktestConfig());
    }
}
