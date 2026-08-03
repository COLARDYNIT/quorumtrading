package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reads and values a simulation's own capital ledger — the authoritative record of a broker-backed
 * simulation's cash and positions.
 *
 * <p>It outranks anything the broker reports for that simulation, for three reasons in order of
 * weight:</p>
 * <ol>
 *   <li><b>An account balance is not a per-simulation figure.</b> One broker account backs many
 *       simulations: a mock account states a <em>starting capital</em>, and an Alpaca account may
 *       host several concurrent LIVE runs. No arithmetic turns one into the other.</li>
 *   <li><b>The ledger is the only record that sees every cash movement.</b> Fills arrive through
 *       {@code BrokerRouter.recordFillInSimulationPortfolio}, top-ups through
 *       {@code SimulationContributionService}. The mock adapter's per-simulation map sees fills
 *       only, so a contribution left it stale.</li>
 *   <li><b>The ledger is durable.</b> The adapter's map is in-memory and is re-seeded from the
 *       account's capital after a restart.</li>
 * </ol>
 *
 * <p>Reading the broker instead is what wrote a $12,000 mock-account capital into a $1,000
 * plan-funded simulation's snapshots every five minutes (+1100% on a portfolio that never traded),
 * and — where contributions had been reserved and the placeholder pre-dated them — a $495 figure
 * onto $12,000 of funding (-95.9%). The broker's job is prices and fills.</p>
 *
 * <p>A broker-less backtest deliberately has no ledger: with no real account to ring-fence there is
 * nothing to isolate, and the mock broker's own state is the single record behind its snapshots.
 * Those callers fall back to the broker, which is why every entry point here takes a fallback.</p>
 *
 * <p>Ports arrive as plain (nullable) references, not CDI {@code Instance} handles: the application
 * layer is CDI-free by architecture rule, so resolving optional beans stays in {@code BrokerRouter}
 * — the one class in this package exempted from that rule.</p>
 */
final class SimulationCapitalLedger {

    private static final Logger LOG = Logger.getLogger(SimulationCapitalLedger.class);

    private SimulationCapitalLedger() {
    }

    /** Short form of a simulation id, for logs. */
    private static String ref(SimulationId simulationId) {
        return simulationId.value().toString().substring(0, 8);
    }

    /**
     * The simulation's ledger, or empty when it has none (broker-less backtest) or persistence is
     * not wired (isolated adapter/test context).
     */
    private static Optional<SimulationPortfolio> read(
            SimulationPortfolioRepository ledgers, SimulationId simulationId) {
        if (ledgers == null) {
            return Optional.empty();
        }
        try {
            return ledgers.findById(simulationId);
        } catch (Exception e) {
            LOG.warnf(e, "Could not read the capital ledger for simulation %s: %s",
                ref(simulationId), e.getMessage());
            return Optional.empty();
        }
    }

    /** This simulation's positions and cash, else {@code fromBroker}. */
    static Portfolio portfolio(
            SimulationPortfolioRepository ledgers,
            SimulationId simulationId,
            Supplier<Portfolio> fromBroker) {

        Optional<SimulationPortfolio> ledger = read(ledgers, simulationId);
        if (ledger.isEmpty()) {
            return fromBroker.get();
        }
        SimulationPortfolio tracked = ledger.get();
        return new Portfolio(simulationId, new HashMap<>(tracked.getPositions()), tracked.getCashAvailable());
    }

    /**
     * This simulation's spendable cash, else {@code fromBroker}. Order sizing reads this, so it
     * must be neither the account's whole capital nor a placeholder that pre-dates a contribution.
     */
    static Money cash(
            SimulationPortfolioRepository ledgers,
            SimulationId simulationId,
            Supplier<Money> fromBroker) {

        return read(ledgers, simulationId)
            .map(SimulationPortfolio::getCashAvailable)
            .orElseGet(fromBroker);
    }

    /**
     * This simulation's positions valued at market plus its cash, else {@code fromBroker}.
     *
     * <p>Returns {@code null} — "cannot be valued accurately" — when a position held by the ledger
     * has no usable price, the same contract the broker adapters honour and which
     * {@code ExecuteConsensusDecisionService} reads as "skip this snapshot". Withholding the figure
     * is the only safe answer: deferring to the broker would reinstate the account-level number
     * this exists to avoid, and {@code SimulationPortfolio.calculateTotalValue} falls back to cost
     * basis for an unpriced symbol, which would present a stale value as a market value.</p>
     */
    static Money value(
            SimulationPortfolioRepository ledgers,
            MarketEventRepository marketEvents,
            SimulationId simulationId,
            Instant timestamp,
            Supplier<Money> fromBroker) {

        Optional<SimulationPortfolio> ledger = read(ledgers, simulationId);
        if (ledger.isEmpty()) {
            return fromBroker.get();
        }
        return valueAtMarket(ledger.get(), marketEvents, simulationId, timestamp);
    }

    private static Money valueAtMarket(
            SimulationPortfolio ledger,
            MarketEventRepository marketEvents,
            SimulationId simulationId,
            Instant timestamp) {

        Map<Symbol, Position> positions = ledger.getPositions();
        if (positions.isEmpty()) {
            return ledger.getCashAvailable();
        }
        if (marketEvents == null) {
            LOG.warnf("Cannot value simulation %s: %d position(s) held but no price source is wired",
                ref(simulationId), positions.size());
            return null;
        }

        Map<Symbol, Money> prices =
            LatestMarketPrices.resolve(marketEvents, positions.keySet(), timestamp);
        for (Symbol symbol : positions.keySet()) {
            if (!prices.containsKey(symbol)) {
                LOG.warnf("Cannot value simulation %s at %s: no market price for %s",
                    ref(simulationId), timestamp, symbol.ticker());
                return null;
            }
        }
        return ledger.calculateTotalValue(prices);
    }

    /**
     * Overwrite the broker's per-simulation state with the ledger's, if there is a ledger.
     *
     * <p>Linking a simulation to an account gives it isolated broker state, but the adapter can
     * only seed that state from the ACCOUNT's starting capital — it has no way to know this
     * simulation's share of it. The router does. Replacing the placeholder here means the account's
     * capital is never what an affordability check inside the broker sees either.</p>
     */
    static void replaceAccountSeed(
            SimulationPortfolioRepository ledgers,
            SimulationId simulationId,
            BrokerPort broker) {

        Optional<SimulationPortfolio> ledger = read(ledgers, simulationId);
        if (ledger.isEmpty()) {
            return;
        }
        SimulationPortfolio tracked = ledger.get();
        broker.initializePortfolio(simulationId, tracked.getCashAvailable(),
            new HashMap<>(tracked.getPositions()));
        LOG.infof("Replaced the account-seeded placeholder for simulation %s with its own ledger: "
            + "cash %s, %d position(s)",
            ref(simulationId), tracked.getCashAvailable(), tracked.getPositions().size());
    }
}
