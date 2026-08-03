package com.quorumtrading.adapter.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.persistence.entity.SimulationPortfolioJpaEntity;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper between SimulationPortfolio domain entities and SimulationPortfolioJpaEntity.
 *
 * <p>Handles the following conversions:</p>
 * <ul>
 *   <li>Value objects (SimulationId, BrokerAccountId, Money) to/from primitives</li>
 *   <li>Map&lt;Symbol, Position&gt; to/from JSONB string</li>
 *   <li>Currency handling for all Money values</li>
 * </ul>
 *
 * <p><b>Position JSON Format:</b></p>
 * <pre>
 * [
 *   {
 *     "symbol": "BTC",
 *     "investmentType": "CRYPTO",
 *     "quantity": "1.5",
 *     "averagePriceAmount": "45000.00",
 *     "currency": "USD"
 *   }
 * ]
 * </pre>
 */
@ApplicationScoped
public class SimulationPortfolioMapper {

    private static final Logger log = LoggerFactory.getLogger(SimulationPortfolioMapper.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts a domain SimulationPortfolio to a JPA entity for persistence.
     *
     * @param portfolio the domain entity
     * @return the JPA entity ready for persistence
     */
    public SimulationPortfolioJpaEntity toJpaEntity(SimulationPortfolio portfolio) {
        SimulationPortfolioJpaEntity entity = new SimulationPortfolioJpaEntity();

        // Map SimulationId (UUID wrapper to UUID)
        entity.setSimulationId(portfolio.getSimulationId().value());

        // Map BrokerAccountId (UUID wrapper to UUID)
        entity.setBrokerAccountId(portfolio.getBrokerAccountId().value());

        // Map Money values (store amounts as BigDecimal). All three Money values share the
        // portfolio's single settlement currency (USD for Alpaca, EUR for Saxo/EU); persist
        // it from cashReserved so the currency round-trips instead of defaulting to USD.
        entity.setCashAvailable(portfolio.getCashAvailable().amount());
        entity.setCashReserved(portfolio.getCashReserved().amount());
        entity.setRealizedPnl(portfolio.getRealizedPnL().amount());
        entity.setCashCurrency(portfolio.getCashReserved().currency().name());

        // Serialize positions to JSON
        entity.setPositions(serializePositions(portfolio.getPositions()));

        // Set timestamp
        entity.setUpdatedAt(Instant.now());

        return entity;
    }

    /**
     * Converts a JPA entity to a domain SimulationPortfolio.
     *
     * @param entity the JPA entity from persistence
     * @return the domain entity with all business logic intact
     */
    public SimulationPortfolio toDomain(SimulationPortfolioJpaEntity entity) {
        // Map SimulationId
        SimulationId simulationId = new SimulationId(entity.getSimulationId());

        // Map BrokerAccountId
        BrokerAccountId brokerAccountId = new BrokerAccountId(entity.getBrokerAccountId());

        // Settlement currency persisted by V119; legacy / null rows fall back to USD so the
        // pre-EU behaviour is byte-identical.
        Money.Currency currency = parseCurrency(entity.getCashCurrency());

        // Map Money values
        Money cashAvailable = new Money(entity.getCashAvailable(), currency);
        Money cashReserved = new Money(entity.getCashReserved(), currency);
        Money realizedPnL = new Money(entity.getRealizedPnl(), currency);

        // Deserialize positions from JSON
        Map<Symbol, Position> positions = deserializePositions(entity.getPositions());

        return new SimulationPortfolio(
            simulationId,
            brokerAccountId,
            positions,
            cashAvailable,
            cashReserved,
            realizedPnL
        );
    }

    /**
     * Resolve the stored settlement currency, defaulting to USD for null / blank / unknown
     * values so legacy rows (pre-V119) and any malformed value stay byte-identical to the
     * historical USD-only behaviour.
     *
     * @param stored the persisted currency code (may be null on legacy rows)
     * @return the parsed currency, or {@code USD} when absent / unrecognised
     */
    private Money.Currency parseCurrency(String stored) {
        if (stored == null || stored.isBlank()) {
            return Money.Currency.USD;
        }
        try {
            return Money.Currency.valueOf(stored.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown simulation_portfolios.cash_currency '{}', defaulting to USD", stored);
            return Money.Currency.USD;
        }
    }

    /**
     * Serializes positions map to JSON string for JSONB storage.
     *
     * @param positions the positions map (symbol to position)
     * @return JSON string representation
     */
    private String serializePositions(Map<Symbol, Position> positions) {
        try {
            // Convert to a list of serializable maps
            List<Map<String, Object>> positionsList = positions.values().stream()
                .map(this::positionToMap)
                .toList();

            return objectMapper.writeValueAsString(positionsList);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize positions", e);
            throw new RuntimeException("Failed to serialize positions", e);
        }
    }

    /**
     * Converts a Position to a serializable map.
     *
     * <p>The {@code symbol} field carries the {@link Symbol#canonical()} form
     * (byte-identical to the bare ticker for US, {@code "ROOT:MIC"} for European
     * venues). The instrument {@code exchange} / {@code uic} / {@code assetType}
     * are written additively, only for European / Saxo-identified instruments, so a
     * US position's map stays compatible with the pre-Saxo blob shape.</p>
     */
    private Map<String, Object> positionToMap(Position position) {
        Symbol symbol = position.symbol();
        Map<String, Object> map = new HashMap<>();
        map.put("symbol", symbol.canonical());
        map.put("investmentType", symbol.type().name());
        map.put("quantity", position.quantity().toString());
        map.put("averagePriceAmount", position.averagePrice().amount().toString());
        map.put("currency", position.averagePrice().currency().name());
        // Additive instrument-identity fields — only for EU / Saxo-identified names.
        if (symbol.isEuropean() || symbol.uic() != null || symbol.assetType() != null) {
            if (symbol.exchange() != null) {
                map.put("exchange", symbol.exchange().code());
            }
            if (symbol.uic() != null) {
                map.put("uic", symbol.uic());
            }
            if (symbol.assetType() != null) {
                map.put("assetType", symbol.assetType().name());
            }
        }
        return map;
    }

    /**
     * Deserializes positions from JSON string.
     *
     * @param json the JSON string from JSONB storage
     * @return the positions map, or empty map on error
     */
    private Map<Symbol, Position> deserializePositions(String json) {
        if (json == null || json.isEmpty() || json.equals("[]") || json.equals("null")) {
            return new HashMap<>();
        }

        try {
            List<Map<String, Object>> positionsList = objectMapper.readValue(
                json,
                new TypeReference<List<Map<String, Object>>>() {}
            );

            Map<Symbol, Position> positions = new HashMap<>();
            for (Map<String, Object> positionMap : positionsList) {
                Position position = mapToPosition(positionMap);
                if (position != null) {
                    positions.put(position.symbol(), position);
                }
            }
            return positions;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize positions from JSON: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * Converts a map to a Position domain object.
     */
    private Position mapToPosition(Map<String, Object> map) {
        try {
            // Parse the (possibly European) symbol from the canonical string.
            String canonical = map.get("symbol").toString();
            InvestmentType investmentType = InvestmentType.valueOf(
                map.getOrDefault("investmentType", "CRYPTO").toString()
            );

            // Parse average-price currency first — it doubles as the instrument's
            // settlement currency when reconstituting an enriched symbol.
            Money.Currency currency = Money.Currency.valueOf(
                map.getOrDefault("currency", "USD").toString()
            );

            Symbol symbol = reconstituteSymbol(canonical, investmentType, currency, map);

            // Parse quantity
            BigDecimal quantity = new BigDecimal(map.get("quantity").toString());

            // Parse average price
            BigDecimal averagePriceAmount = new BigDecimal(map.get("averagePriceAmount").toString());
            Money averagePrice = new Money(averagePriceAmount, currency);

            return new Position(symbol, quantity, averagePrice);
        } catch (Exception e) {
            log.warn("Failed to parse position from map: {}", map, e);
            return null;
        }
    }

    /**
     * Reconstitute a {@link Symbol} from a serialized position map.
     *
     * <p><b>Legacy fallback:</b> a blob written before the Saxo/EU keystone carries
     * only {@code symbol} (bare US ticker), {@code investmentType} and
     * {@code currency} — no {@code exchange}/{@code uic}/{@code assetType}. With no
     * exchange field present we hydrate through
     * {@link SymbolParser#parse(String, InvestmentType, Money.Currency)} on the
     * canonical string, which for a bare US ticker yields the byte-identical US
     * symbol and never throws.</p>
     *
     * <p><b>Enriched form:</b> when the additive fields are present (European /
     * Saxo-identified instrument) we restore the full venue identity including the
     * numeric {@code uic} and {@code assetType}.</p>
     */
    private Symbol reconstituteSymbol(String canonical, InvestmentType type,
                                      Money.Currency currency, Map<String, Object> map) {
        Object exchangeObj = map.get("exchange");
        Object uicObj = map.get("uic");
        Object assetTypeObj = map.get("assetType");

        // Legacy blob (no enrichment fields) → US/USD via the canonical form. Pass
        // the stored currency through so a US blob keeps USD and never forks identity.
        if (exchangeObj == null && uicObj == null && assetTypeObj == null) {
            return SymbolParser.parse(canonical, type, currency);
        }

        // Enriched blob — strip the MIC suffix off the canonical "ROOT:MIC" form to
        // recover the bare ticker root, then restore the full instrument identity.
        String ticker = canonical;
        int colon = canonical.indexOf(':');
        if (colon >= 0) {
            ticker = canonical.substring(0, colon);
        }
        MIC exchange = exchangeObj != null ? new MIC(exchangeObj.toString()) : null;
        Long uic = uicObj != null ? Long.valueOf(uicObj.toString()) : null;
        AssetType assetType = assetTypeObj != null ? AssetType.valueOf(assetTypeObj.toString()) : null;
        return new Symbol(ticker, type, exchange, currency, uic, assetType);
    }
}
