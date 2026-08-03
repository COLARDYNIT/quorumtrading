package com.quorumtrading.application.orchestration.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.model.enums.ConsensusActionType;
import com.quorumtrading.model.valueobject.ConsensusAction;

import java.math.BigDecimal;

/**
 * THE single boundary where LLM-originated consensus action text becomes typed.
 *
 * <p>{@link #parse(String)} is a total, pure function: it never throws and maps every
 * input to a {@link ConsensusAction} (with {@link ConsensusActionType#UNKNOWN} as the
 * sink). It subsumes the three historical ad-hoc parsers with their divergent fallbacks:</p>
 *
 * <ul>
 *   <li><b>P1</b> {@code ExecuteConsensusDecisionService.extractActionFromResponse} — the
 *       real-money path. Its deliberate leniency is preserved bit-for-bit: malformed /
 *       unterminated JSON with a recognizable action still resolves (see
 *       {@link #salvageScan}), and {@link ConsensusAction#actionToken()} carries the exact
 *       token so failure messages stay identical.</li>
 *   <li><b>P2</b> {@code ReachConsensusService.extractActionType} — cooldown note text.</li>
 *   <li><b>P3</b> {@code ReachConsensusService.extractActionLabel} — prompt history labels
 *       (now {@link ConsensusActionType#pastTenseLabel()}).</li>
 *   <li><b>P4</b> {@code ReachConsensusService.parseQuantityFromJson} — quantity extraction
 *       (any failure → {@code null} quantity, exactly as before).</li>
 * </ul>
 *
 * <p>The typed factories ({@link #hold}, {@link #skip}, {@link #skipNoMarketData}) delegate
 * to {@link AgentResponseParser#buildActionJson} so the serialized {@code raw} form — the
 * only thing persisted and exposed over the wire — is byte-identical to the historical
 * output.</p>
 */
public final class ConsensusActionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The exact legacy no-market-data SKIP literal (note: {@code "reason"}, not {@code "reasoning"}). */
    private static final String SKIP_NO_MARKET_DATA_JSON =
        "{\"action\": \"SKIP\", \"reason\": \"No market data available\"}";

    private ConsensusActionParser() {
    }

    /**
     * Parse a raw finalAction string into a typed {@link ConsensusAction}.
     * Total function — never throws; the original string is preserved as
     * {@link ConsensusAction#raw()} unmodified.
     */
    public static ConsensusAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ConsensusAction.unknown(raw, null);
        }

        String trimmed = raw.trim();

        // Plain token (also covers legacy V3-era rows storing bare "BUY"/"SELL")
        ConsensusActionType plainType = ConsensusActionType.fromToken(trimmed);
        if (plainType != ConsensusActionType.UNKNOWN) {
            return new ConsensusAction(plainType, trimmed, null, null, null, null, raw);
        }

        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = MAPPER.readTree(trimmed);
                return fromJsonNode(node, raw);
            } catch (Exception e) {
                // Malformed JSON — replicate the money path's lenient char-scan salvage
                return salvageScan(trimmed, raw);
            }
        }

        return ConsensusAction.unknown(raw, null);
    }

    private static ConsensusAction fromJsonNode(JsonNode node, String raw) {
        String token = textOrNull(node, "action");
        ConsensusActionType type = token != null ? ConsensusActionType.fromToken(token) : ConsensusActionType.UNKNOWN;

        String symbol = textOrNull(node, "symbol");
        BigDecimal quantity = decimalOrNull(node, "quantity");
        BigDecimal confidence = decimalOrNull(node, "confidence");
        String reasoning = textOrNull(node, "reasoning");
        if (reasoning == null) {
            reasoning = textOrNull(node, "reason"); // legacy no-market-data SKIP shape
        }

        return new ConsensusAction(type, token, symbol, quantity, confidence, reasoning, raw);
    }

    /**
     * Byte-for-byte replication of the historical money-path char-scan for malformed JSON:
     * find {@code "action"}, skip the colon / whitespace / opening quote, read the value up
     * to a closing quote or comma. A resolvable token yields that type with <b>no payload</b>
     * (quantity {@code null} is safe: the historical parser never read quantity from
     * malformed JSON either — the quantity column on the decision is separate).
     */
    private static ConsensusAction salvageScan(String trimmed, String raw) {
        int actionIndex = trimmed.indexOf("\"action\"");
        if (actionIndex == -1) {
            return ConsensusAction.unknown(raw, null);
        }
        int colonIndex = trimmed.indexOf(':', actionIndex);
        if (colonIndex == -1) {
            return ConsensusAction.unknown(raw, null);
        }
        int valueStart = colonIndex + 1;
        while (valueStart < trimmed.length()
                && (Character.isWhitespace(trimmed.charAt(valueStart)) || trimmed.charAt(valueStart) == '"')) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < trimmed.length()
                && trimmed.charAt(valueEnd) != '"' && trimmed.charAt(valueEnd) != ',') {
            valueEnd++;
        }
        String token = trimmed.substring(valueStart, valueEnd).trim();
        ConsensusActionType type = ConsensusActionType.fromToken(token);
        return new ConsensusAction(type, token, null, null, null, null, raw);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (Exception e) {
            return null; // matches historical parseQuantityFromJson: unparseable → null
        }
    }

    // ---------- typed factories (render via AgentResponseParser for byte-identical raw) ----------

    /** W1 shape: system-generated HOLD (majority defaults, ties, abstentions). */
    public static ConsensusAction hold(String symbol, double quantity, double confidence, String reasoning) {
        return parse(AgentResponseParser.buildActionJson("HOLD", symbol, quantity, confidence, reasoning));
    }

    /** W1 shape: system-generated SKIP (all agents failed, cooldown). */
    public static ConsensusAction skip(String symbol, String reasoning) {
        return parse(AgentResponseParser.buildSkipActionJson(symbol, reasoning));
    }

    /** W3 shape: the verbatim legacy no-market-data SKIP literal. */
    public static ConsensusAction skipNoMarketData() {
        return parse(SKIP_NO_MARKET_DATA_JSON);
    }

    /** W2 shape: cleaned LLM JSON from an agent or the orchestrator. Semantic alias for {@link #parse}. */
    public static ConsensusAction fromLlmJson(String cleanedResponse) {
        return parse(cleanedResponse);
    }
}
