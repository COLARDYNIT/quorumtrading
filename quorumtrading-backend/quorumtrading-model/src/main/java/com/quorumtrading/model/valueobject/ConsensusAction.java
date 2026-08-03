package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.ConsensusActionType;

import java.math.BigDecimal;

/**
 * Immutable, parsed-once view of a consensus decision's final action.
 *
 * <p>Pure data — no Jackson, no parsing. The single place LLM-originated action text
 * becomes typed is {@code ConsensusActionParser} in the application layer; everywhere
 * else consumes this value object.</p>
 *
 * @param type        resolved action type — never {@code null} ({@link ConsensusActionType#UNKNOWN} for unparseable input)
 * @param actionToken the exact action text as it appeared in the source (e.g. {@code "BUY"},
 *                    {@code "FOO"}); {@code null} when no action token was present. Preserved
 *                    so failure messages on the trade-execution path stay byte-identical to
 *                    the historical ad-hoc parser.
 * @param symbol      symbol from the action payload, nullable
 * @param quantity    quantity from the action payload; {@code null} when absent or unparseable
 * @param confidence  confidence from the action payload, nullable
 * @param reasoning   reasoning text (from {@code "reasoning"} or the legacy {@code "reason"} key), nullable
 * @param raw         the canonical string form — the ONLY representation persisted and exposed
 *                    over the wire; {@code null} only for a null source string
 */
public record ConsensusAction(
        ConsensusActionType type,
        String actionToken,
        String symbol,
        BigDecimal quantity,
        BigDecimal confidence,
        String reasoning,
        String raw
) {

    public ConsensusAction {
        if (type == null) {
            throw new IllegalArgumentException("ConsensusActionType must not be null");
        }
    }

    /** An unresolved action: type UNKNOWN with the raw text and optional token preserved. */
    public static ConsensusAction unknown(String raw, String actionToken) {
        return new ConsensusAction(ConsensusActionType.UNKNOWN, actionToken, null, null, null, null, raw);
    }
}
