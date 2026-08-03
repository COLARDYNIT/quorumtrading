package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.PortfolioJpaEntity;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper between PortfolioSnapshot domain entity and PortfolioJpaEntity.
 * Handles JSON serialization/deserialization of positions.
 */
@ApplicationScoped
public class PortfolioMapper {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert domain PortfolioSnapshot to JPA entity.
     * Serializes positions Map to JSON string.
     */
    public PortfolioJpaEntity toJpaEntity(PortfolioSnapshot snapshot) {
        PortfolioJpaEntity entity = new PortfolioJpaEntity();

        entity.setSimulationId(snapshot.getSimulationId().value());
        entity.setTimestamp(snapshot.getTimestamp());
        entity.setCashAvailable(snapshot.getCashAvailable().amount());
        entity.setTotalValueUsd(snapshot.getTotalValue().amount());

        // Serialize positions Map to JSON string
        entity.setPositionsJson(serializePositions(snapshot.getPositions()));

        return entity;
    }

    /**
     * Serialize positions Map<Symbol, Position> to JSON string.
     * Includes currentPrice field (nullable for backward compatibility).
     *
     * <p>The {@code "symbol"} field carries the {@link Symbol#canonical()} form,
     * which is <b>byte-identical to the bare ticker for US instruments</b> (so
     * existing blobs and dedup are unchanged) and {@code "ROOT:MIC"} for European
     * venues. The instrument's {@code exchange} / {@code currency} / {@code uic} /
     * {@code assetType} are written additively (only when non-default) so an EU
     * symbol round-trips; their absence on a legacy blob hydrates as US/USD.</p>
     */
    private String serializePositions(Map<Symbol, Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            Position pos = entry.getValue();
            Symbol sym = pos.symbol();
            json.append("{")
                    .append("\"symbol\":\"").append(sym.canonical()).append("\",")
                    .append("\"type\":\"").append(sym.type()).append("\",");
            // Additive instrument-identity fields — emitted ONLY for European /
            // Saxo-identified instruments so that a US position's blob stays
            // byte-identical to the pre-Saxo format (no spurious "currency":"USD").
            if (sym.isEuropean() || sym.uic() != null || sym.assetType() != null) {
                if (sym.exchange() != null) {
                    json.append("\"exchange\":\"").append(sym.exchange().code()).append("\",");
                }
                if (sym.currency() != null) {
                    json.append("\"currency\":\"").append(sym.currency().name()).append("\",");
                }
                if (sym.uic() != null) {
                    json.append("\"uic\":").append(sym.uic()).append(",");
                }
                if (sym.assetType() != null) {
                    json.append("\"assetType\":\"").append(sym.assetType().name()).append("\",");
                }
            }
            json.append("\"quantity\":").append(pos.quantity()).append(",")
                    .append("\"averagePrice\":").append(pos.averagePrice().amount()).append(",")
                    .append("\"currentPrice\":")
                    .append(pos.currentPrice() != null ? pos.currentPrice().amount() : "null")
                    .append("}");
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Convert JPA entity to domain PortfolioSnapshot.
     * Deserializes positions JSON string to Map.
     *
     * NOTE: Uses setters instead of constructor to handle legacy corrupted data ($0 values)
     * that may exist in the database from before validation was added.
     * Returns null for corrupted records, which callers should filter out.
     */
    public PortfolioSnapshot toDomain(PortfolioJpaEntity entity) {
        SimulationId simulationId = new SimulationId(entity.getSimulationId());

        // Currency is always USD (based on database schema: total_value_usd, cash_available are in USD)
        Money cashAvailable = new Money(
            entity.getCashAvailable() != null ? entity.getCashAvailable() : BigDecimal.ZERO,
            Money.Currency.USD
        );

        Money totalValue = new Money(
            entity.getTotalValueUsd() != null ? entity.getTotalValueUsd() : BigDecimal.ZERO,
            Money.Currency.USD
        );

        // Validate totalValue - skip corrupted records with $0 or negative values
        // These exist from before validation was added and indicate API failures
        if (totalValue.amount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Skipping corrupted portfolio snapshot {} for simulation {}: " +
                       "totalValue is {} (should be positive). This data predates validation.",
                       entity.id, simulationId.value(), totalValue.amount());
            return null;
        }

        // Deserialize positions JSON string to Map
        Map<Symbol, Position> positions = deserializePositions(entity.getPositionsJson());

        // Use setters to hydrate from DB (bypasses constructor validation for edge cases)
        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.setId(entity.id);
        snapshot.setSimulationId(simulationId);
        snapshot.setTimestamp(entity.getTimestamp());
        snapshot.setPositions(positions);
        snapshot.setTotalValue(totalValue);
        snapshot.setCashAvailable(cashAvailable);

        return snapshot;
    }

    /**
     * Deserialize positions JSON string to Map<Symbol, Position>.
     * Handles both new format (with type and currentPrice fields) and old format for backward compatibility.
     */
    private Map<Symbol, Position> deserializePositions(String positionsJson) {
        Map<Symbol, Position> positions = new HashMap<>();

        if (positionsJson == null || positionsJson.isEmpty() || positionsJson.equals("[]")) {
            return positions;
        }

        try {
            // Parse as array of maps
            List<Map<String, Object>> positionList = objectMapper.readValue(
                positionsJson,
                new TypeReference<List<Map<String, Object>>>() {}
            );

            // Convert each position map to Position object
            for (Map<String, Object> posMap : positionList) {
                String canonical = (String) posMap.get("symbol");
                Object typeObj = posMap.get("type");  // May be null for old data
                Object quantityObj = posMap.get("quantity");
                Object averagePriceObj = posMap.get("averagePrice");
                Object currentPriceObj = posMap.get("currentPrice");  // May be null for old data or when market data unavailable

                if (canonical != null && quantityObj != null && averagePriceObj != null) {
                    // Convert to BigDecimal (handles both Integer and Double from JSON)
                    BigDecimal quantity = new BigDecimal(quantityObj.toString());
                    BigDecimal averagePrice = new BigDecimal(averagePriceObj.toString());

                    // Resolve investment type: explicit field, else legacy default STOCK.
                    InvestmentType type;
                    if (typeObj != null) {
                        type = InvestmentType.valueOf(typeObj.toString());
                    } else {
                        type = InvestmentType.STOCK;
                        logger.debug("Deserialized position from old format (no type field): {}", canonical);
                    }

                    // Reconstitute the (possibly European) Symbol. A legacy blob has no
                    // exchange/currency/uic/assetType fields → it hydrates as US/USD via
                    // the canonical form; an enriched blob restores the full venue identity.
                    Symbol symbolObj = reconstituteSymbol(canonical, type, posMap);

                    Money avgPrice = new Money(averagePrice, Money.Currency.USD);

                    // Parse currentPrice if present (nullable for backward compatibility)
                    Money currentPrice = null;
                    if (currentPriceObj != null && !(currentPriceObj instanceof String && currentPriceObj.equals("null"))) {
                        BigDecimal currentPriceValue = new BigDecimal(currentPriceObj.toString());
                        currentPrice = new Money(currentPriceValue, Money.Currency.USD);
                    }

                    // Create Position with currentPrice (null if old data or unavailable)
                    positions.put(symbolObj, new Position(symbolObj, quantity, avgPrice, currentPrice));

                    logger.debug("Deserialized position: {} (type: {}) x {} @ avg:{} current:{}",
                        symbolObj.canonical(), symbolObj.type(), quantity, averagePrice, currentPrice);
                }
            }

            logger.debug("Deserialized {} positions from JSON", positions.size());

        } catch (Exception e) {
            logger.error("Failed to deserialize positions JSON: {}", positionsJson, e);
            // Return empty positions on parse error
        }

        return positions;
    }

    /**
     * Reconstitute a {@link Symbol} from a serialized position map.
     *
     * <p><b>Legacy fallback:</b> a position blob written before the Saxo/EU keystone
     * carries only {@code symbol} (bare US ticker) + {@code type}. With no
     * {@code exchange}/{@code currency}/{@code uic}/{@code assetType} fields present
     * we hydrate through {@link SymbolParser#parse(String, InvestmentType)} on the
     * canonical string, which for a bare US ticker yields the byte-identical US/USD
     * symbol — never throwing.</p>
     *
     * <p><b>Enriched form:</b> when the additive fields are present (European /
     * Saxo-identified instrument) we restore the full venue identity, including the
     * numeric {@code uic} and {@code assetType} that the canonical string alone
     * cannot carry.</p>
     */
    private Symbol reconstituteSymbol(String canonical, InvestmentType type, Map<String, Object> posMap) {
        Object exchangeObj = posMap.get("exchange");
        Object currencyObj = posMap.get("currency");
        Object uicObj = posMap.get("uic");
        Object assetTypeObj = posMap.get("assetType");

        // Legacy blob (no enrichment fields) → US/USD via the canonical form.
        if (exchangeObj == null && currencyObj == null && uicObj == null && assetTypeObj == null) {
            return SymbolParser.parse(canonical, type);
        }

        // Enriched blob — restore the full instrument identity. The serialized
        // "symbol" is the canonical "ROOT:MIC" form; strip the MIC suffix to recover
        // the bare ticker root that the Symbol record stores.
        String ticker = canonical;
        int colon = canonical.indexOf(':');
        if (colon >= 0) {
            ticker = canonical.substring(0, colon);
        }
        MIC exchange = exchangeObj != null ? new MIC(exchangeObj.toString()) : null;
        Money.Currency currency = currencyObj != null
            ? Money.Currency.valueOf(currencyObj.toString())
            : Money.Currency.USD;
        Long uic = uicObj != null ? Long.valueOf(uicObj.toString()) : null;
        AssetType assetType = assetTypeObj != null ? AssetType.valueOf(assetTypeObj.toString()) : null;
        return new Symbol(ticker, type, exchange, currency, uic, assetType);
    }
}
