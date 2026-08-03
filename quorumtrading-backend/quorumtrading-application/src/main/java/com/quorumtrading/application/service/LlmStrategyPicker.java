package com.quorumtrading.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.Strategy.StrategyStatus;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UsageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 3.1 LLM-backed strategy picker. Asks an LLM to choose one strategy from
 * the user's ACTIVE pool given the plan's risk/ticker preferences.
 *
 * <p>Fails open: any LLM error, parse error, or hallucinated strategy id triggers
 * a fall-back to the heuristic picker so an investment plan never breaks because
 * of a transient LLM hiccup. The fall-back is surfaced in the reasoning string so
 * users can see when AI selection was unavailable.</p>
 */
public class LlmStrategyPicker implements StrategyPickerPort {

    private static final Logger logger = LoggerFactory.getLogger(LlmStrategyPicker.class);
    private static final String PROMPT_TEMPLATE = "/prompts/strategy-picker.md";
    private static final int CONTENT_EXCERPT_CHARS = 800;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final LLMPort llmPort;
    private final StrategyRepository strategyRepository;
    private final StrategyPickerPort fallbackPicker;
    private final RecordLLMUsageUseCase recordLLMUsageUseCase;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public LlmStrategyPicker(
            LLMPort llmPort,
            StrategyRepository strategyRepository,
            StrategyPickerPort fallbackPicker,
            RecordLLMUsageUseCase recordLLMUsageUseCase,
            String model,
            double temperature,
            int maxTokens) {
        this.llmPort = llmPort;
        this.strategyRepository = strategyRepository;
        this.fallbackPicker = fallbackPicker;
        this.recordLLMUsageUseCase = recordLLMUsageUseCase;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public Outcome pick(InvestmentPlan plan) {
        List<Strategy> active = strategyRepository.findByOwnerId(plan.getUserId()).stream()
                .filter(s -> s.getStatus() == StrategyStatus.ACTIVE)
                .sorted(Comparator.comparing(Strategy::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (active.isEmpty()) {
            return Outcome.none("No active strategies owned by user — create one before scheduling investments.");
        }

        List<Strategy> eligible = new ArrayList<>();
        List<String> policyRejections = new ArrayList<>();
        for (Strategy s : active) {
            TickerPolicyEnforcer.Decision decision =
                    TickerPolicyEnforcer.evaluate(s, plan.getPreferences());
            if (decision.allowed()) {
                eligible.add(s);
            } else {
                policyRejections.add(s.getName() + ": " + decision.reason());
            }
        }

        if (eligible.isEmpty()) {
            return Outcome.none("All " + active.size()
                    + " active strategies were filtered by ticker policy — "
                    + String.join("; ", policyRejections));
        }

        String prompt;
        LLMQueryResult result;
        try {
            prompt = renderPrompt(plan, eligible);
            LLMConfig config = new LLMConfig(model, temperature, maxTokens, null, plan.getUserId().value());
            result = llmPort.queryWithUsage(prompt, config);
        } catch (RuntimeException e) {
            logger.warn("LLM picker failed for plan {}, falling back to heuristic: {}",
                    plan.getId(), e.getMessage());
            return annotateFallback(fallbackPicker.pick(plan), "LLM call failed: " + e.getMessage());
        }

        // Record LLM usage for budget tracking (mirrors FindTickerService).
        LLMUsage callUsage = result.usage();
        if (callUsage != null && callUsage.totalTokens() > 0) {
            recordLLMUsageUseCase.recordUsage(
                    plan.getUserId(), callUsage.billedCost(), callUsage.rawCost(),
                    callUsage.totalTokens(), UsageSource.CHAT);
        }

        Optional<StrategyId> chosen;
        String reasoning;
        try {
            JsonNode parsed = JSON.readTree(stripJsonFence(result.content()));
            String reasoningText = parsed.has("reasoning") ? parsed.get("reasoning").asText("") : "";
            reasoning = reasoningText.isBlank() ? "(LLM returned no reasoning)" : reasoningText;

            JsonNode idNode = parsed.get("chosenStrategyId");
            if (idNode == null || idNode.isNull()) {
                chosen = Optional.empty();
            } else {
                UUID parsedId = UUID.fromString(idNode.asText());
                chosen = eligible.stream()
                        .map(Strategy::getId)
                        .filter(sid -> sid.value().equals(parsedId))
                        .findFirst();
                if (chosen.isEmpty()) {
                    logger.warn("LLM picker for plan {} returned unknown strategy id {}, falling back to heuristic",
                            plan.getId(), parsedId);
                    return annotateFallback(fallbackPicker.pick(plan),
                            "LLM returned an unknown strategy id (" + parsedId + ")");
                }
            }
        } catch (RuntimeException | java.io.IOException e) {
            logger.warn("LLM picker for plan {} produced unparseable response, falling back to heuristic: {}",
                    plan.getId(), e.getMessage());
            return annotateFallback(fallbackPicker.pick(plan), "LLM response unparseable: " + e.getMessage());
        }

        LLMUsage usage = result.usage();
        java.math.BigDecimal cost = usage == null ? java.math.BigDecimal.ZERO : usage.billedCost();

        if (chosen.isEmpty()) {
            logger.info("LLM picker declined for plan {}: {}", plan.getId(), reasoning);
            return new Outcome(Optional.empty(), "LLM picker: " + reasoning, cost);
        }
        logger.info("LLM picker chose strategy {} for plan {} (cost=${})",
                chosen.get(), plan.getId(), cost);
        return Outcome.chose(chosen.get(), "LLM picker: " + reasoning, cost);
    }

    private Outcome annotateFallback(Outcome fallback, String failureNote) {
        String prefix = "[LLM picker unavailable — " + failureNote + "] ";
        return new Outcome(fallback.chosenStrategyId(), prefix + fallback.reasoning(), fallback.llmCostUsd());
    }

    private String renderPrompt(InvestmentPlan plan, List<Strategy> eligible) {
        InvestmentPlanPreferences prefs = plan.getPreferences();
        Map<String, String> ctx = Map.of(
                "riskTolerance", nullToDash(prefs.riskTolerance()),
                "preferExistingHoldings", String.valueOf(prefs.preferExistingHoldings()),
                "maxStrategiesActive", prefs.maxStrategiesActive() == null
                        ? "(unlimited)" : prefs.maxStrategiesActive().toString(),
                "allowedTickers", prefs.allowedTickers().isEmpty()
                        ? "(any)" : String.join(", ", prefs.allowedTickers()),
                "blockedTickers", prefs.blockedTickers().isEmpty()
                        ? "(none)" : String.join(", ", prefs.blockedTickers()),
                "candidates", renderCandidates(eligible));

        String template = loadTemplate();
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            template = template.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return template;
    }

    private String renderCandidates(List<Strategy> eligible) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < eligible.size(); i++) {
            Strategy s = eligible.get(i);
            String content = s.getContent() == null ? "" : s.getContent();
            String excerpt = content.length() > CONTENT_EXCERPT_CHARS
                    ? content.substring(0, CONTENT_EXCERPT_CHARS) + "…"
                    : content;
            sb.append("[").append(i).append("] ")
                    .append("id=").append(s.getId().value()).append(" ")
                    .append("name=").append(s.getName()).append(" ")
                    .append("updatedAt=").append(s.getUpdatedAt()).append("\n")
                    .append("description: ").append(nullToDash(s.getDescription())).append("\n")
                    .append("content (excerpt):\n").append(excerpt).append("\n\n");
        }
        return sb.toString();
    }

    private String loadTemplate() {
        try (InputStream is = getClass().getResourceAsStream(PROMPT_TEMPLATE)) {
            if (is == null) {
                throw new IllegalStateException("Prompt template not found: " + PROMPT_TEMPLATE);
            }
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read prompt template " + PROMPT_TEMPLATE, e);
        }
    }

    private static String stripJsonFence(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
