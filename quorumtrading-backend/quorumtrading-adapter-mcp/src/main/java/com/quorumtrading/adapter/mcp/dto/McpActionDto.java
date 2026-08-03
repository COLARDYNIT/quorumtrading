package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A decision's action payload, as structured fields rather than an embedded JSON string.
 *
 * <p>Both the orchestrator's {@code finalAction} and an agent's {@code recommendedAction}
 * are persisted as LLM-authored JSON text. Shipping that text verbatim inside a JSON
 * response forced every consumer to {@code JSON.parse} a string nested inside JSON in
 * order to read fields the schema already advertised. This record is that payload,
 * parsed once at the adapter boundary.</p>
 *
 * <p>{@code raw} is populated <b>only</b> when the payload could not be parsed
 * ({@code action == null}), so malformed LLM output stays diagnosable without
 * duplicating well-formed output that is already fully represented by the other
 * fields.</p>
 *
 * @param action     resolved action token (BUY / SELL / HOLD / SKIP), or {@code null} if unparseable
 * @param symbol     symbol from the action payload, nullable
 * @param quantity   quantity from the action payload, nullable
 * @param confidence confidence from the action payload, nullable
 * @param reasoning  reasoning text; {@code null} when reasoning was not requested, and always
 *                   {@code null} on an agent's {@code recommendedAction} (where the sibling
 *                   {@code reasoning} field carries it instead, so the text is never repeated)
 * @param raw        the original unparsed text — populated only when parsing failed
 */
@RegisterForReflection
public record McpActionDto(
    String action,
    String symbol,
    String quantity,
    String confidence,
    String reasoning,
    String raw
) {}
