package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.ConsensusDecisionJpaEntity;
import com.quorumtrading.adapter.persistence.entity.PortfolioJpaEntity;
import com.quorumtrading.adapter.persistence.entity.SimulationPortfolioJpaEntity;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression suite for the Saxo/EU {@code Symbol} enrichment landing in the three
 * persistence mappers ({@link PortfolioMapper}, {@link SimulationPortfolioMapper},
 * {@link ConsensusDecisionMapper}).
 *
 * <p>Covers, per the IS2 work-package acceptance:</p>
 * <ul>
 *   <li><b>US round-trip</b> — a bare US symbol survives serialize → deserialize
 *       unchanged, and the persisted form is byte-identical to the pre-Saxo blob.</li>
 *   <li><b>EU round-trip</b> — an exchange-qualified European symbol
 *       (MIC + currency + uic + assetType) survives the round-trip.</li>
 *   <li><b>Legacy fallback</b> — a blob / column written before the keystone (no
 *       enrichment fields) reconstitutes as US/USD without throwing.</li>
 *   <li><b>Dedup regression</b> — two logically-equal US instruments still collapse
 *       to one map key because identity is over {@code (canonical(), type)}.</li>
 * </ul>
 *
 * <p>These mappers serialize to/from JSON strings and plain columns, so they are
 * exercised directly as POJOs — no database round-trip is needed to prove the
 * Symbol-mapping contract.</p>
 */
class SymbolRoundTripMapperTest {

    private final PortfolioMapper portfolioMapper = new PortfolioMapper();
    private final SimulationPortfolioMapper simPortfolioMapper = new SimulationPortfolioMapper();
    private final ConsensusDecisionMapper consensusMapper = new ConsensusDecisionMapper();

    private static final UUID SIM_ID = UUID.randomUUID();
    private static final UUID BROKER_ID = UUID.randomUUID();

    private static final Symbol US_AAPL = new Symbol("AAPL", InvestmentType.STOCK);
    private static final Symbol EU_RHM = new Symbol(
        "RHM", InvestmentType.STOCK, new MIC("XETR"), Money.Currency.EUR, 12345L, AssetType.STOCK);

    // ---------------------------------------------------------------------------
    // PortfolioMapper
    // ---------------------------------------------------------------------------

    @Test
    void portfolioMapper_roundTripsUsSymbol() {
        PortfolioSnapshot snapshot = portfolioSnapshot(Map.of(
            US_AAPL, new Position(US_AAPL, new BigDecimal("10"), Money.usd(new BigDecimal("150.00")),
                Money.usd(new BigDecimal("155.00")))));

        PortfolioSnapshot back = portfolioMapper.toDomain(portfolioMapper.toJpaEntity(snapshot));

        assertNotNull(back);
        Position pos = back.getPositions().get(US_AAPL);
        assertNotNull(pos, "US symbol must survive round-trip");
        assertEquals("AAPL", pos.symbol().canonical());
        assertFalse(pos.symbol().isEuropean());
        assertEquals(Money.Currency.USD, pos.symbol().currency());
    }

    @Test
    void portfolioMapper_usBlobIsByteIdenticalToLegacyFormat() {
        // The pre-Saxo serializer wrote symbol/type/quantity/averagePrice/currentPrice
        // with the bare ticker. canonical()==ticker for US, and we emit NO enrichment
        // fields for US, so the produced blob must match the historical shape exactly.
        PortfolioSnapshot snapshot = portfolioSnapshot(Map.of(
            US_AAPL, new Position(US_AAPL, new BigDecimal("10"), Money.usd(new BigDecimal("150.00")),
                Money.usd(new BigDecimal("155.00")))));

        String json = portfolioMapper.toJpaEntity(snapshot).getPositionsJson();

        assertEquals(
            "[{\"symbol\":\"AAPL\",\"type\":\"STOCK\",\"quantity\":10,"
                + "\"averagePrice\":150.00,\"currentPrice\":155.00}]",
            json);
    }

    @Test
    void portfolioMapper_roundTripsEuSymbol() {
        PortfolioSnapshot snapshot = portfolioSnapshot(Map.of(
            EU_RHM, new Position(EU_RHM, new BigDecimal("4"),
                new Money(new BigDecimal("520.00"), Money.Currency.EUR), null)));

        PortfolioJpaEntity entity = portfolioMapper.toJpaEntity(snapshot);
        // The canonical form (ROOT:MIC) and the enrichment fields are present.
        assertTrue(entity.getPositionsJson().contains("\"symbol\":\"RHM:XETR\""));
        assertTrue(entity.getPositionsJson().contains("\"exchange\":\"XETR\""));
        assertTrue(entity.getPositionsJson().contains("\"currency\":\"EUR\""));
        assertTrue(entity.getPositionsJson().contains("\"uic\":12345"));
        assertTrue(entity.getPositionsJson().contains("\"assetType\":\"STOCK\""));

        PortfolioSnapshot back = portfolioMapper.toDomain(entity);
        Position pos = back.getPositions().get(EU_RHM);
        assertNotNull(pos, "EU symbol must survive round-trip");
        Symbol s = pos.symbol();
        assertEquals("RHM:XETR", s.canonical());
        assertTrue(s.isEuropean());
        assertEquals("XETR", s.exchange().code());
        assertEquals(Money.Currency.EUR, s.currency());
        assertEquals(12345L, s.uic());
        assertEquals(AssetType.STOCK, s.assetType());
    }

    @Test
    void portfolioMapper_legacyBlobWithoutNewFieldsReconstitutesAsUsUsd() {
        PortfolioJpaEntity entity = new PortfolioJpaEntity();
        entity.setSimulationId(SIM_ID);
        entity.setTimestamp(Instant.now());
        entity.setCashAvailable(new BigDecimal("1000.00"));
        entity.setTotalValueUsd(new BigDecimal("2500.00"));
        // Legacy blob: no exchange/currency/uic/assetType, bare ticker.
        entity.setPositionsJson(
            "[{\"symbol\":\"MSFT\",\"type\":\"STOCK\",\"quantity\":5,"
                + "\"averagePrice\":300.00,\"currentPrice\":null}]");

        PortfolioSnapshot back = portfolioMapper.toDomain(entity);

        assertNotNull(back);
        Symbol legacy = new Symbol("MSFT", InvestmentType.STOCK);
        Position pos = back.getPositions().get(legacy);
        assertNotNull(pos, "legacy US blob must hydrate without throwing");
        assertEquals("MSFT", pos.symbol().canonical());
        assertFalse(pos.symbol().isEuropean());
        assertEquals(Money.Currency.USD, pos.symbol().currency());
    }

    @Test
    void portfolioMapper_legacyBlobWithoutTypeFieldReconstitutes() {
        // Oldest format: no "type" field at all → defaults to STOCK, US/USD.
        PortfolioJpaEntity entity = new PortfolioJpaEntity();
        entity.setSimulationId(SIM_ID);
        entity.setTimestamp(Instant.now());
        entity.setCashAvailable(new BigDecimal("1000.00"));
        entity.setTotalValueUsd(new BigDecimal("2500.00"));
        entity.setPositionsJson("[{\"symbol\":\"IBM\",\"quantity\":3,\"averagePrice\":120.00}]");

        PortfolioSnapshot back = portfolioMapper.toDomain(entity);

        Position pos = back.getPositions().get(new Symbol("IBM", InvestmentType.STOCK));
        assertNotNull(pos);
        assertEquals("IBM", pos.symbol().canonical());
        assertEquals(InvestmentType.STOCK, pos.symbol().type());
    }

    // ---------------------------------------------------------------------------
    // SimulationPortfolioMapper
    // ---------------------------------------------------------------------------

    @Test
    void simPortfolioMapper_roundTripsUsSymbol() {
        SimulationPortfolio portfolio = simPortfolio(Map.of(
            US_AAPL, new Position(US_AAPL, new BigDecimal("10"),
                Money.usd(new BigDecimal("150.00")))));

        SimulationPortfolioJpaEntity usEntity = simPortfolioMapper.toJpaEntity(portfolio);
        // USD regression: the cash currency column persists "USD" and round-trips unchanged.
        assertEquals("USD", usEntity.getCashCurrency());

        SimulationPortfolio back = simPortfolioMapper.toDomain(usEntity);

        Position pos = back.getPositions().get(US_AAPL);
        assertNotNull(pos);
        assertEquals("AAPL", pos.symbol().canonical());
        assertFalse(pos.symbol().isEuropean());
        assertEquals(Money.Currency.USD, pos.averagePrice().currency());
        // Cash Money values reconstitute in USD.
        assertEquals(Money.Currency.USD, back.getCashAvailable().currency());
        assertEquals(Money.Currency.USD, back.getCashReserved().currency());
        assertEquals(Money.Currency.USD, back.getRealizedPnL().currency());
    }

    @Test
    void simPortfolioMapper_roundTripsEuSymbol() {
        Position euPos = new Position(EU_RHM, new BigDecimal("4"),
            new Money(new BigDecimal("520.00"), Money.Currency.EUR));
        SimulationPortfolio portfolio = simPortfolioEur(Map.of(EU_RHM, euPos));

        SimulationPortfolioJpaEntity entity = simPortfolioMapper.toJpaEntity(portfolio);
        assertTrue(entity.getPositions().contains("\"symbol\":\"RHM:XETR\""));
        assertTrue(entity.getPositions().contains("\"exchange\":\"XETR\""));
        assertTrue(entity.getPositions().contains("\"uic\":12345"));
        assertTrue(entity.getPositions().contains("\"assetType\":\"STOCK\""));
        // CUR1: the portfolio's EUR settlement currency is persisted (V119), not defaulted to USD.
        assertEquals("EUR", entity.getCashCurrency());

        SimulationPortfolio back = simPortfolioMapper.toDomain(entity);
        Position pos = back.getPositions().get(EU_RHM);
        assertNotNull(pos);
        Symbol s = pos.symbol();
        assertEquals("RHM:XETR", s.canonical());
        assertTrue(s.isEuropean());
        assertEquals("XETR", s.exchange().code());
        assertEquals(Money.Currency.EUR, s.currency());
        assertEquals(12345L, s.uic());
        assertEquals(AssetType.STOCK, s.assetType());
        assertEquals(Money.Currency.EUR, pos.averagePrice().currency());
        // CUR1: cash Money values reconstitute as EUR — the same-currency thread survives reload.
        assertEquals(Money.Currency.EUR, back.getCashAvailable().currency());
        assertEquals(Money.Currency.EUR, back.getCashReserved().currency());
        assertEquals(Money.Currency.EUR, back.getRealizedPnL().currency());
        assertEquals(new BigDecimal("1000.00"), back.getCashAvailable().amount());
    }

    @Test
    void simPortfolioMapper_legacyBlobReconstitutesAsUsUsd() {
        SimulationPortfolioJpaEntity entity = new SimulationPortfolioJpaEntity();
        entity.setSimulationId(SIM_ID);
        entity.setBrokerAccountId(BROKER_ID);
        entity.setCashAvailable(new BigDecimal("1000.00"));
        entity.setCashReserved(BigDecimal.ZERO);
        entity.setRealizedPnl(BigDecimal.ZERO);
        // Legacy blob: symbol/investmentType/quantity/averagePriceAmount/currency only.
        entity.setPositions(
            "[{\"symbol\":\"TSLA\",\"investmentType\":\"STOCK\","
                + "\"quantity\":\"2\",\"averagePriceAmount\":\"700.00\",\"currency\":\"USD\"}]");

        // Legacy row predates V119 → cash_currency is NULL; the mapper must default to USD.
        assertNull(entity.getCashCurrency());

        SimulationPortfolio back = simPortfolioMapper.toDomain(entity);

        Position pos = back.getPositions().get(new Symbol("TSLA", InvestmentType.STOCK));
        assertNotNull(pos, "legacy US blob must hydrate without throwing");
        assertEquals("TSLA", pos.symbol().canonical());
        assertFalse(pos.symbol().isEuropean());
        assertEquals(Money.Currency.USD, pos.symbol().currency());
        // Cash defaults to USD on a legacy (null cash_currency) row — byte-identical behaviour.
        assertEquals(Money.Currency.USD, back.getCashAvailable().currency());
    }

    // ---------------------------------------------------------------------------
    // ConsensusDecisionMapper
    // ---------------------------------------------------------------------------

    @Test
    void consensusMapper_roundTripsUsSymbol_columnIsBareTicker() {
        ConsensusDecision decision = consensusDecision(US_AAPL, InvestmentType.STOCK);

        ConsensusDecisionJpaEntity entity = consensusMapper.toJpaEntity(decision);
        // The symbol column stores canonical() == bare ticker for US (byte-identical).
        assertEquals("AAPL", entity.symbol);

        ConsensusDecision back = consensusMapper.toDomain(entity);
        assertEquals(US_AAPL, back.getSymbol());
        assertEquals("AAPL", back.getSymbol().canonical());
        assertFalse(back.getSymbol().isEuropean());
        assertEquals(InvestmentType.STOCK, back.getInvestmentType());
    }

    @Test
    void consensusMapper_roundTripsCryptoSymbol() {
        // Pins the persist→reload path for crypto: a BTC decision must come back CRYPTO, not the
        // STOCK default the InvestmentType javadoc warns about.
        ConsensusDecision decision = consensusDecision(new Symbol("BTC", InvestmentType.CRYPTO), InvestmentType.CRYPTO);

        ConsensusDecisionJpaEntity entity = consensusMapper.toJpaEntity(decision);
        assertEquals("BTC", entity.symbol);
        assertEquals("CRYPTO", entity.investmentType);

        ConsensusDecision back = consensusMapper.toDomain(entity);
        assertEquals(InvestmentType.CRYPTO, back.getInvestmentType());
        assertEquals(InvestmentType.CRYPTO, back.getSymbol().type());
        assertEquals("BTC", back.getSymbol().canonical());
        assertFalse(back.getSymbol().isEuropean());
    }

    @Test
    void consensusMapper_roundTripsEuSymbol_columnIsCanonical() {
        ConsensusDecision decision = consensusDecision(EU_RHM, InvestmentType.STOCK);

        ConsensusDecisionJpaEntity entity = consensusMapper.toJpaEntity(decision);
        // Canonical "ROOT:MIC" fits the VARCHAR(20) column widened by V116.
        assertEquals("RHM:XETR", entity.symbol);
        assertTrue(entity.symbol.length() <= 20);

        ConsensusDecision back = consensusMapper.toDomain(entity);
        Symbol s = back.getSymbol();
        assertEquals("RHM:XETR", s.canonical());
        assertTrue(s.isEuropean());
        assertEquals("XETR", s.exchange().code());
        // Currency recovered from the MIC even though the column carries no currency.
        assertEquals(Money.Currency.EUR, s.currency());
        assertEquals(InvestmentType.STOCK, back.getInvestmentType());
    }

    @Test
    void consensusMapper_legacyColumnReconstitutesAsUsUsd() {
        ConsensusDecisionJpaEntity entity = new ConsensusDecisionJpaEntity();
        entity.simulationId = SIM_ID;
        entity.decisionTimestamp = Instant.now();
        // Legacy row: bare US ticker in the column, type alongside.
        entity.symbol = "NVDA";
        entity.investmentType = "STOCK";

        ConsensusDecision back = consensusMapper.toDomain(entity);

        Symbol s = back.getSymbol();
        assertNotNull(s, "legacy US column must hydrate without throwing");
        assertEquals("NVDA", s.canonical());
        assertFalse(s.isEuropean());
        assertEquals(Money.Currency.USD, s.currency());
        assertEquals(InvestmentType.STOCK, back.getInvestmentType());
    }

    @Test
    void consensusMapper_nullSymbolColumnYieldsNull() {
        ConsensusDecisionJpaEntity entity = new ConsensusDecisionJpaEntity();
        entity.simulationId = SIM_ID;
        entity.decisionTimestamp = Instant.now();
        entity.symbol = null;
        entity.investmentType = null;

        ConsensusDecision back = consensusMapper.toDomain(entity);

        assertNull(back.getSymbol());
    }

    // ---------------------------------------------------------------------------
    // Dedup / identity regression — duplicate US instruments collapse to one key
    // because equals/hashCode is over (canonical(), type).
    // ---------------------------------------------------------------------------

    @Test
    void duplicateUsSymbols_stillDedupToOnePositionKey() {
        // Two logically-equal US symbols (one built via the 2-arg legacy ctor, one
        // via the full ctor with an explicit US MIC) MUST collapse to a single key
        // both in the domain map and after a serialize → deserialize round-trip.
        Symbol legacyAapl = new Symbol("AAPL", InvestmentType.STOCK);
        Symbol fullAapl = new Symbol("AAPL", InvestmentType.STOCK,
            MIC.US_DEFAULT, Money.Currency.USD, null, null);

        assertEquals(legacyAapl, fullAapl, "US identity must be stable under enrichment");
        assertEquals(legacyAapl.hashCode(), fullAapl.hashCode());

        Map<Symbol, Position> positions = new HashMap<>();
        positions.put(legacyAapl, new Position(legacyAapl, new BigDecimal("1"),
            Money.usd(new BigDecimal("150.00"))));
        positions.put(fullAapl, new Position(fullAapl, new BigDecimal("2"),
            Money.usd(new BigDecimal("160.00"))));
        assertEquals(1, positions.size(), "duplicate US symbols must dedup before persistence");

        PortfolioSnapshot back = portfolioMapper.toDomain(
            portfolioMapper.toJpaEntity(portfolioSnapshot(positions)));
        assertEquals(1, back.getPositions().size(),
            "duplicate US symbols must remain deduped after a persistence round-trip");

        SimulationPortfolio simBack = simPortfolioMapper.toDomain(
            simPortfolioMapper.toJpaEntity(simPortfolio(positions)));
        assertEquals(1, simBack.getPositions().size(),
            "duplicate US symbols must remain deduped through the sim-portfolio mapper");
    }

    @Test
    void canonicalForUsIsByteIdenticalToBareTicker() {
        // The dedup invariant rests on canonical()==ticker for US: assert it directly
        // so the price-bar hash / symbol=? query / position-limit key stay stable.
        assertEquals("AAPL", US_AAPL.canonical());
        assertEquals("AAPL", new Symbol("AAPL", InvestmentType.STOCK,
            MIC.US_DEFAULT, Money.Currency.USD, null, null).canonical());
        assertEquals("BRK.B", new Symbol("BRK.B", InvestmentType.STOCK).canonical());
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private PortfolioSnapshot portfolioSnapshot(Map<Symbol, Position> positions) {
        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.setSimulationId(new SimulationId(SIM_ID));
        snapshot.setTimestamp(Instant.now());
        snapshot.setPositions(positions);
        snapshot.setCashAvailable(Money.usd(new BigDecimal("1000.00")));
        snapshot.setTotalValue(Money.usd(new BigDecimal("2500.00")));
        return snapshot;
    }

    private SimulationPortfolio simPortfolio(Map<Symbol, Position> positions) {
        return new SimulationPortfolio(
            new SimulationId(SIM_ID),
            new com.quorumtrading.model.valueobject.BrokerAccountId(BROKER_ID),
            new HashMap<>(positions),
            Money.usd(new BigDecimal("1000.00")),
            Money.usd(BigDecimal.ZERO),
            Money.usd(BigDecimal.ZERO));
    }

    private SimulationPortfolio simPortfolioEur(Map<Symbol, Position> positions) {
        return new SimulationPortfolio(
            new SimulationId(SIM_ID),
            new com.quorumtrading.model.valueobject.BrokerAccountId(BROKER_ID),
            new HashMap<>(positions),
            new Money(new BigDecimal("1000.00"), Money.Currency.EUR),
            new Money(BigDecimal.ZERO, Money.Currency.EUR),
            new Money(BigDecimal.ZERO, Money.Currency.EUR));
    }

    private ConsensusDecision consensusDecision(Symbol symbol, InvestmentType type) {
        ConsensusDecision decision = new ConsensusDecision();
        decision.setSimulationId(new SimulationId(SIM_ID));
        decision.setDecisionTimestamp(Instant.now());
        decision.setFinalAction(ConsensusActionParser.parse("BUY"));
        decision.setExecuted(true);
        decision.setSymbol(symbol);
        decision.setInvestmentType(type);
        decision.setQuantity(new BigDecimal("1"));
        return decision;
    }
}
