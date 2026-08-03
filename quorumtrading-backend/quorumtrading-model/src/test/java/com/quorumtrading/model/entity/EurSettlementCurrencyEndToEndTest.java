package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * End-to-end currency-thread proof (CUR1): a EUR (Belgian/Saxo) settlement currency must
 * flow account → {@link SimulationPortfolio} → consensus/contribution {@link Money}
 * arithmetic <b>without</b> tripping {@link Money}'s same-currency guard, while the
 * USD/Alpaca path stays byte-identical.
 *
 * <p>Pure domain test — no infrastructure. It exercises the exact arithmetic the
 * orchestration + consensus + contribution services drive: buy with an instrument-currency
 * price, contribution top-up, unrealized P&L and total-value valuation, and the
 * MIC → currency inference used when a stored price event carries no currency.</p>
 */
class EurSettlementCurrencyEndToEndTest {

    private static final SaxoOAuthCredentials SAXO_CREDS = new SaxoOAuthCredentials(
        "app-key", "app-secret", "refresh-token", "access-token",
        Instant.parse("2026-06-04T12:00:00Z"), SaxoEnvironment.LIVE);

    @Nested
    class EurAccountThreadsEurEndToEnd {

        @Test
        void saxoEurAccountReportsEurSettlementCurrency() {
            Money eurBalance = Money.of(new BigDecimal("10000.00"), Money.Currency.EUR);
            BrokerAccount account = BrokerAccount.createReal(
                UserId.generate(), "My Saxo EU", BrokerType.SAXO_LIVE, SAXO_CREDS, eurBalance, eurBalance);

            assertThat(account.getSettlementCurrency()).isEqualTo(Money.Currency.EUR);
        }

        @Test
        void eurPortfolioInitializesAndTradesWithoutSameCurrencyThrow() {
            // Account EUR -> EUR trading limit -> EUR SimulationPortfolio.
            Money eurBalance = Money.of(new BigDecimal("10000.00"), Money.Currency.EUR);
            BrokerAccount account = BrokerAccount.createReal(
                UserId.generate(), "My Saxo EU", BrokerType.SAXO_LIVE, SAXO_CREDS, eurBalance, eurBalance);

            Money tradingLimit = Money.of(new BigDecimal("5000.00"), account.getSettlementCurrency());
            SimulationPortfolio portfolio = SimulationPortfolio.initialize(
                SimulationId.generate(), account.getId(), tradingLimit);

            assertThat(portfolio.getCashAvailable().currency()).isEqualTo(Money.Currency.EUR);

            // A EUR-venue instrument priced in EUR, the way SymbolParser stamps it.
            Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
            Money eurPrice = Money.of(new BigDecimal("500.00"), SymbolParser.currencyFor(rhm));
            assertThat(eurPrice.currency()).isEqualTo(Money.Currency.EUR);

            // Buy 6 shares @ €500 = €3000 — all EUR arithmetic, must not throw.
            assertThatCode(() -> portfolio.recordBuy(
                rhm, new BigDecimal("6"), eurPrice,
                Money.of(new BigDecimal("3000.00"), Money.Currency.EUR)))
                .doesNotThrowAnyException();
            assertThat(portfolio.getCashAvailable())
                .isEqualTo(Money.of(new BigDecimal("2000.00"), Money.Currency.EUR));

            // Contribution top-up (DCA) in EUR — recordContribution does Money.add, must not throw.
            assertThatCode(() -> portfolio.recordContribution(
                Money.of(new BigDecimal("1000.00"), Money.Currency.EUR)))
                .doesNotThrowAnyException();
            assertThat(portfolio.getCashAvailable())
                .isEqualTo(Money.of(new BigDecimal("3000.00"), Money.Currency.EUR));

            // Valuation with a EUR price map — calculateUnrealizedPnL / calculateTotalValue
            // reduce over Money.add seeded in the reserved currency; must stay EUR.
            Map<Symbol, Money> prices = Map.of(
                rhm, Money.of(new BigDecimal("550.00"), Money.Currency.EUR));

            Money unrealized = portfolio.calculateUnrealizedPnL(prices);
            assertThat(unrealized.currency()).isEqualTo(Money.Currency.EUR);
            // 6 * (550 - 500) = 300
            assertThat(unrealized.amount()).isEqualByComparingTo(new BigDecimal("300.00"));

            Money totalValue = portfolio.calculateTotalValue(prices);
            assertThat(totalValue.currency()).isEqualTo(Money.Currency.EUR);
            // cash 3000 + 6 * 550 = 6300
            assertThat(totalValue.amount()).isEqualByComparingTo(new BigDecimal("6300.00"));
        }

        @Test
        void eurAccountReserveAndContributionStaySingleCurrency() {
            Money eurBalance = Money.of(new BigDecimal("10000.00"), Money.Currency.EUR);
            BrokerAccount account = BrokerAccount.createReal(
                UserId.generate(), "My Saxo EU", BrokerType.SAXO_LIVE, SAXO_CREDS, eurBalance, eurBalance);

            // BrokerAccount.reserve does Money.subtract/add — same EUR currency, no throw.
            assertThatCode(() -> account.reserve(Money.of(new BigDecimal("2500.00"), Money.Currency.EUR)))
                .doesNotThrowAnyException();
            assertThat(account.getReservedBalance().currency()).isEqualTo(Money.Currency.EUR);
            assertThat(account.getAvailableBalance())
                .isEqualTo(Money.of(new BigDecimal("7500.00"), Money.Currency.EUR));
        }

        @Test
        void priceCurrencyInferredFromEuMicWhenEventCarriesNone() {
            // Stored price events carry only a number; the consensus path infers the currency
            // from the position's venue. A EU-venue symbol must yield EUR.
            Symbol euSymbol = new Symbol(
                "RHM", InvestmentType.STOCK, new MIC("XETR"), Money.Currency.EUR, null, null);
            Money inferred = Money.of(new BigDecimal("123.45"), SymbolParser.currencyFor(euSymbol));
            assertThat(inferred.currency()).isEqualTo(Money.Currency.EUR);
        }
    }

    @Nested
    class UsdRegressionStaysByteIdentical {

        @Test
        void alpacaUsdAccountReportsUsdSettlementCurrency() {
            Money usdBalance = Money.usd(new BigDecimal("25000.00"));
            BrokerAccount account = BrokerAccount.createReal(
                UserId.generate(), "My Alpaca", BrokerType.ALPACA_PAPER,
                new ApiKeyCredentials("PKTEST", "secret"), usdBalance, usdBalance);

            assertThat(account.getSettlementCurrency()).isEqualTo(Money.Currency.USD);
        }

        @Test
        void usdPortfolioTradesAndValuesInUsdUnchanged() {
            BrokerAccountId accountId = BrokerAccountId.generate();
            Money tradingLimit = Money.usd(new BigDecimal("10000.00"));
            SimulationPortfolio portfolio = SimulationPortfolio.initialize(
                SimulationId.generate(), accountId, tradingLimit);

            assertThat(portfolio.getCashAvailable().currency()).isEqualTo(Money.Currency.USD);

            Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
            // Bare US ticker -> USD inferred, byte-identical to the legacy Money.usd path.
            assertThat(SymbolParser.currencyFor(aapl)).isEqualTo(Money.Currency.USD);

            Money usdPrice = Money.usd(new BigDecimal("150.00"));
            portfolio.recordBuy(aapl, new BigDecimal("10"), usdPrice, Money.usd(new BigDecimal("1500.00")));
            assertThat(portfolio.getCashAvailable())
                .isEqualTo(Money.usd(new BigDecimal("8500.00")));

            Map<Symbol, Money> prices = Map.of(aapl, Money.usd(new BigDecimal("160.00")));
            Money totalValue = portfolio.calculateTotalValue(prices);
            assertThat(totalValue.currency()).isEqualTo(Money.Currency.USD);
            // 8500 cash + 10 * 160 = 10100
            assertThat(totalValue.amount()).isEqualByComparingTo(new BigDecimal("10100.00"));
        }
    }
}
