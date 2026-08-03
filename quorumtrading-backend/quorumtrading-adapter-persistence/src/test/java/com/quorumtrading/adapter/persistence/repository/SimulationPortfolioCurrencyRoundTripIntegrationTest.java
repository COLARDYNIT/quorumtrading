package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database round-trip for the CUR1 currency thread: a {@link SimulationPortfolio} created in
 * a EUR (Saxo) account's settlement currency must persist and reload as EUR — proving the
 * V119 {@code cash_currency} column + mapper carry the currency through the real repository +
 * Flyway-migrated {@code quorumtrading_test} schema, instead of the mapper's old hard-coded
 * USD. A USD (Alpaca) portfolio is the byte-identical regression.
 *
 * <p>Exercises the full FK chain (user → strategy → simulation → broker account →
 * simulation_portfolio), so the test also confirms the migration applied cleanly on a fresh
 * test database.</p>
 */
@QuarkusTest
class SimulationPortfolioCurrencyRoundTripIntegrationTest {

    @Inject
    SimulationPortfolioRepository portfolioRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    @Inject
    UserRepository userRepository;

    @Test
    void eurPortfolioRoundTripsAsEur() {
        UserId userId = createUser();
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
            "app-key", "app-secret", "refresh-token", "access-token",
            Instant.parse("2026-06-04T12:00:00Z"), SaxoEnvironment.LIVE);
        Money eurBalance = Money.of(new BigDecimal("10000.00"), Money.Currency.EUR);
        BrokerAccount account = brokerAccountRepository.save(BrokerAccount.createReal(
            userId, "Saxo EU", BrokerType.SAXO_LIVE, creds, eurBalance, eurBalance));
        // Settlement currency is carried by the account balance.
        assertThat(account.getSettlementCurrency()).isEqualTo(Money.Currency.EUR);

        SimulationId simId = createSimulation(userId, Money.of(new BigDecimal("5000.00"), Money.Currency.EUR));

        // Portfolio minted in the account's settlement currency, with a EUR-priced EU position.
        SimulationPortfolio portfolio = SimulationPortfolio.initialize(
            simId, account.getId(), Money.of(new BigDecimal("5000.00"), account.getSettlementCurrency()));
        com.quorumtrading.model.valueobject.Symbol rhm = new com.quorumtrading.model.valueobject.Symbol(
            "RHM", InvestmentType.STOCK, new MIC("XETR"), Money.Currency.EUR, 12345L, null);
        portfolio.recordBuy(rhm, new BigDecimal("4"),
            Money.of(new BigDecimal("500.00"), Money.Currency.EUR),
            Money.of(new BigDecimal("2000.00"), Money.Currency.EUR));

        portfolioRepository.save(portfolio);

        SimulationPortfolio reloaded = portfolioRepository.findById(simId).orElseThrow();
        assertThat(reloaded.getCashAvailable().currency()).isEqualTo(Money.Currency.EUR);
        assertThat(reloaded.getCashReserved().currency()).isEqualTo(Money.Currency.EUR);
        assertThat(reloaded.getRealizedPnL().currency()).isEqualTo(Money.Currency.EUR);
        assertThat(reloaded.getCashAvailable().amount()).isEqualByComparingTo(new BigDecimal("3000.00"));
        // EU position survives with its EUR average price — no same-currency throw on reload.
        assertThat(reloaded.getPositions().get(rhm).averagePrice().currency())
            .isEqualTo(Money.Currency.EUR);
    }

    @Test
    void usdPortfolioRoundTripsAsUsdUnchanged() {
        UserId userId = createUser();
        Money usdBalance = Money.usd(new BigDecimal("25000.00"));
        BrokerAccount account = brokerAccountRepository.save(BrokerAccount.createReal(
            userId, "Alpaca", BrokerType.ALPACA_PAPER,
            new ApiKeyCredentials("PKTEST", "secret"), usdBalance, usdBalance));
        assertThat(account.getSettlementCurrency()).isEqualTo(Money.Currency.USD);

        SimulationId simId = createSimulation(userId, Money.usd(new BigDecimal("10000.00")));

        SimulationPortfolio portfolio = SimulationPortfolio.initialize(
            simId, account.getId(), Money.usd(new BigDecimal("10000.00")));

        portfolioRepository.save(portfolio);

        SimulationPortfolio reloaded = portfolioRepository.findById(simId).orElseThrow();
        assertThat(reloaded.getCashAvailable().currency()).isEqualTo(Money.Currency.USD);
        assertThat(reloaded.getCashReserved().currency()).isEqualTo(Money.Currency.USD);
        assertThat(reloaded.getRealizedPnL().currency()).isEqualTo(Money.Currency.USD);
        assertThat(reloaded.getCashAvailable().amount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    private UserId createUser() {
        UserId userId = UserId.generate();
        userRepository.save(User.create(
            userId, "sim-portfolio-ccy-" + UUID.randomUUID() + "@example.com",
            "Sim Portfolio Currency", null, OAuthProvider.GOOGLE));
        return userId;
    }

    private SimulationId createSimulation(UserId userId, Money initialCash) {
        Strategy strategy = strategyRepository.save(new Strategy(
            new StrategyId(UUID.randomUUID()), "Currency Test Strategy",
            "## Test\nSimple test strategy", userId));

        SimulationId simId = new SimulationId(UUID.randomUUID());
        Simulation sim = new Simulation(
            simId, "Currency Test Simulation", strategy.getId(),
            Instant.now().minus(30, ChronoUnit.DAYS), Instant.now(), initialCash);
        sim.setOwnerUserId(userId);
        simulationRepository.save(sim);
        return simId;
    }
}
