package com.quorumtrading.application.orchestration.agents;

/**
 * Single source of truth for the JSON response format instruction
 * appended to all agent prompts.
 */
public final class AgentResponseFormat {

    private AgentResponseFormat() {
    }

    /**
     * The JSON response instruction that every agent prompt must end with.
     * Agents may append additional format blocks after this (e.g. Orchestrator's A2UI).
     */
    public static final String JSON_RESPONSE_INSTRUCTION = """

            Respond in JSON format ONLY (no additional text, code, or suggestions):
            {"action":"BUY","symbol":"BTC","confidence":0.75,"reasoning":"Your analysis reasoning here"}

            The "quantity" field is OPTIONAL and is expressed in UNITS of the asset — shares for a stock, \
            coins for a crypto. It is NEVER a cash amount. If you decide to deploy a dollar figure, divide it \
            by the current price yourself and put the resulting unit count here: writing the dollar amount \
            instead buys far more than you intended. \
            If you omit it or set it to 0, the system will automatically \
            use the maximum quantity allowed by the strategy (e.g., all available cash for BUY, all held shares for SELL). \
            Only include "quantity" if you want a PARTIAL position (e.g., scale in/out gradually).

            IMPORTANT: omitting "quantity" means going straight to the maximum position the strategy permits. \
            If the strategy asks you to scale in, enter in tranches, build a position gradually, or avoid \
            all-in entries, you MUST include an explicit "quantity" — otherwise that instruction is silently \
            ignored and the first BUY consumes the entire position limit.

            CRITICAL: The "action" field in your JSON MUST match the conclusion of your reasoning. \
            Write your reasoning FIRST, reach a conclusion (BUY/SELL/HOLD), then set the "action" field to that SAME conclusion. \
            If your reasoning says SELL, the action MUST be SELL. Never contradict your own analysis.
            """;

    /**
     * Appends the standard JSON response instruction to the given prompt.
     */
    public static String appendResponseFormat(String prompt) {
        return prompt + JSON_RESPONSE_INSTRUCTION;
    }
}
