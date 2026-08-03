package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.ConsensusDecisionJpaEntity;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper between ConsensusDecision domain model and ConsensusDecisionJpaEntity.
 *
 * ARCHITECTURAL NOTE:
 * - Domain SimulationId (UUID wrapper) → Database simulationId (UUID)
 * - Domain List<Long> llmDecisionIds → Database Long[] array
 * - Domain ConsensusMethod (enum) → Database consensusMethod (String)
 * - Domain Money (value object) → Database executionPrice (BigDecimal)
 *
 * NOTE: Domain fields 'symbol' and 'investmentType' are not persisted in consensus_decisions table.
 * These may be stored elsewhere or retrieved from related entities.
 */
@ApplicationScoped
public class ConsensusDecisionMapper {

    /**
     * Convert domain ConsensusDecision to JPA entity.
     *
     * Unwraps value objects:
     * - SimulationId.value() → UUID
     * - List<Long> → Long[] array
     * - ConsensusMethod → String
     * - Money → BigDecimal
     */
    public ConsensusDecisionJpaEntity toJpaEntity(ConsensusDecision decision) {
        ConsensusDecisionJpaEntity entity = new ConsensusDecisionJpaEntity();

        entity.id = decision.getId();
        entity.simulationId = decision.getSimulationId() != null ? decision.getSimulationId().value() : null;
        entity.decisionTimestamp = decision.getDecisionTimestamp();

        // Convert List<Long> to Long[] array
        List<Long> decisionIds = decision.getLlmDecisionIds();
        entity.llmDecisionIds = decisionIds != null ? decisionIds.toArray(new Long[0]) : new Long[0];

        entity.consensusMethod = decision.getConsensusMethod() != null ? decision.getConsensusMethod().name() : null;
        entity.finalAction = decision.getFinalAction();
        entity.executed = decision.getExecuted();
        entity.executionPrice = decision.getExecutionPrice() != null ? decision.getExecutionPrice().amount() : null;
        entity.notes = decision.getNotes();
        // Persist the canonical identity (byte-identical to the bare ticker for US
        // instruments; "ROOT:MIC" for European venues) so an EU symbol round-trips
        // through the now-VARCHAR(20) column. Legacy US rows continue to hold the
        // bare ticker because canonical() == ticker for US.
        entity.symbol = decision.getSymbol() != null ? decision.getSymbol().canonical() : null;
        entity.investmentType = decision.getInvestmentType() != null ? decision.getInvestmentType().name() : null;
        entity.quantity = decision.getQuantity();
        entity.executionOutcome = decision.getExecutionOutcome() != null ? decision.getExecutionOutcome().name() : null;

        // Map LLM cost tracking fields
        LLMUsage usage = decision.getLLMUsage();
        if (usage != null) {
            entity.promptTokens = usage.promptTokens();
            entity.completionTokens = usage.completionTokens();
            entity.totalTokens = usage.totalTokens();
            entity.rawCostUsd = usage.rawCost();
        }

        return entity;
    }

    /**
     * Convert JPA entity to domain ConsensusDecision.
     *
     * Wraps database types into value objects:
     * - UUID → SimulationId
     * - Long[] array → List<Long>
     * - String → ConsensusMethod
     * - BigDecimal → Money
     */
    public ConsensusDecision toDomain(ConsensusDecisionJpaEntity entity) {
        ConsensusDecision decision = new ConsensusDecision();

        decision.setId(entity.id);
        decision.setSimulationId(entity.simulationId != null ? new SimulationId(entity.simulationId) : null);
        decision.setDecisionTimestamp(entity.decisionTimestamp);

        // Convert Long[] array to List<Long>
        List<Long> decisionIds = entity.llmDecisionIds != null
            ? Arrays.asList(entity.llmDecisionIds)
            : new ArrayList<>();
        decision.setLlmDecisionIds(decisionIds);

        decision.setConsensusMethod(entity.consensusMethod != null ? parseConsensusMethod(entity.consensusMethod) : null);
        // Parse-on-read: the JSONB column keeps storing the raw string (write path is
        // ConsensusAction.raw(), byte-identical); the typed view is derived here once.
        // Legacy shapes degrade exactly as before: plain "BUY" scalars resolve, W3
        // no-symbol SKIP rows pick up reasoning from the legacy "reason" key, and
        // unparseable rows become UNKNOWN (never tradeable).
        decision.setFinalAction(ConsensusActionParser.parse(entity.finalAction));
        decision.setExecuted(entity.executed != null ? entity.executed : false);
        decision.setExecutionPrice(entity.executionPrice != null ? new Money(entity.executionPrice, Money.Currency.USD) : null);
        decision.setNotes(entity.notes);
        decision.setSymbol(reconstituteSymbol(entity.symbol, entity.investmentType));
        decision.setInvestmentType(entity.investmentType != null ? InvestmentType.valueOf(entity.investmentType) : null);
        decision.setQuantity(entity.quantity);
        decision.setExecutionOutcome(entity.executionOutcome != null ? ExecutionOutcome.valueOf(entity.executionOutcome) : ExecutionOutcome.PENDING);

        // Map LLM cost tracking fields
        LLMUsage usage = LLMUsage.of(
            entity.promptTokens != null ? entity.promptTokens : 0,
            entity.completionTokens != null ? entity.completionTokens : 0,
            entity.totalTokens != null ? entity.totalTokens : 0,
            entity.rawCostUsd != null ? entity.rawCostUsd : BigDecimal.ZERO
        );
        decision.setLLMUsage(usage);

        return decision;
    }

    /**
     * Reconstitute a {@link Symbol} from the stored canonical column plus the
     * explicit investment-type column.
     *
     * <p>Legacy US rows hold a bare ticker (e.g. {@code "AAPL"}) — {@link Symbol#parse(String)}
     * yields a US/USD symbol whose {@code canonical()} is byte-identical, so dedup
     * over {@code (canonical(), type)} is unchanged. European rows hold the
     * {@code "ROOT:MIC"} canonical form (e.g. {@code "RHM:XETR"}) which parses back
     * into the venue-qualified symbol. The investment type stored in its own column
     * always wins over the heuristic {@code parse()} inference so identity is exact.
     * Returns {@code null} when no symbol was stored.</p>
     */
    private static Symbol reconstituteSymbol(String canonical, String investmentTypeName) {
        if (canonical == null) {
            return null;
        }
        InvestmentType type = investmentTypeName != null
            ? InvestmentType.valueOf(investmentTypeName)
            : InvestmentType.STOCK;
        // SymbolParser handles both the bare US ticker (US/USD default, byte-identical
        // canonical) and the "ROOT:MIC" European form (recovering the venue currency
        // from the MIC), keying off the authoritative stored investment type.
        return SymbolParser.parse(canonical, type);
    }

    /**
     * Safely parse ConsensusMethod from database VARCHAR, mapping removed values to MAJORITY.
     */
    private static ConsensusMethod parseConsensusMethod(String value) {
        return switch (value) {
            case "WEIGHTED", "WEIGHTED_AVERAGE", "CONFIDENCE_BASED", "MAJORITY_VOTE" -> ConsensusMethod.MAJORITY;
            default -> {
                try {
                    yield ConsensusMethod.valueOf(value);
                } catch (IllegalArgumentException e) {
                    yield ConsensusMethod.MAJORITY;
                }
            }
        };
    }
}
