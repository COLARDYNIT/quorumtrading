package com.quorumtrading.adapter.mcp;

import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.application.usecase.result.PruneUnusedResult;
import com.quorumtrading.application.usecase.result.TickerPerformanceResult;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.*;
import com.quorumtrading.model.marketdata.BackfillProgress;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers domain and application-layer classes for GraalVM native image reflection.
 * <p>
 * Jackson requires reflection access to serialize domain objects in native image mode.
 * Both MCP tool handlers and ChatToolExecutor serialize these types via ObjectMapper.
 * <p>
 * <b>IMPORTANT:</b> When adding new domain types that are serialized by ChatToolExecutor
 * or MCP tools, add them here. Include both top-level types AND their nested value objects.
 * The {@code ChatToolSerializationTest} will catch missing registrations.
 * <p>
 * By registering them here (in the adapter layer), we avoid polluting domain entities
 * with infrastructure annotations like {@code @RegisterForReflection}.
 */
@RegisterForReflection(targets = {
    // === Application DTOs ===
    MarketResearchSummary.class,
    MarketResearchDetails.class,
    ConsensusWithAgentsResponse.class,
    com.quorumtrading.model.entity.LlmParseFailure.class,
    PruneUnusedResult.class,
    TickerPerformanceResult.class,
    DividendInfo.class,
    TickerSearchResult.class,
    // Nested in TickerSearchResult — a native build serialises these to empty
    // objects without explicit registration, silently dropping the alternative
    // listings and the resolution/confidence fields find_ticker now reports.
    com.quorumtrading.model.valueobject.InstrumentListing.class,
    com.quorumtrading.model.enums.TickerResolution.class,
    com.quorumtrading.model.enums.ConfidenceLevel.class,
    com.quorumtrading.model.enums.ListingType.class,

    // === Entities (serialized directly by ChatToolExecutor) ===
    PortfolioSnapshot.class,
    ConsensusDecision.class,
    LLMDecision.class,
    MarketResearch.class,
    ResearchExecution.class,
    ResearchAgentDecision.class,

    // === Value Objects (transitive dependencies of entities above) ===
    SimulationId.class,
    StrategyId.class,
    Symbol.class,
    Money.class,
    Money.Currency.class,
    LLMUsage.class,
    Position.class,
    Percentage.class,
    ViabilityScore.class,
    ResearchInterval.class,
    ResearchInterval.Unit.class,
    BacktestConfiguration.class,
    UserId.class,
    StrategyPerformance.class,
    IndicatorContext.class,
    IndicatorSnapshot.class,
    IndicatorValue.class,
    IndicatorValue.MovingAverageValue.class,
    IndicatorValue.RSIValue.class,
    IndicatorValue.MACDValue.class,
    IndicatorValue.BollingerValue.class,

    // === Market Data ===
    MarketEvent.class,
    BackfillProgress.class,

    // === Historical Events ===
    HistoricalEvent.class,
    EventCategory.class,
    AssetClass.class,
    MarketSentiment.class,

    // === Enums (serialized as part of entities/VOs above) ===
    InvestmentType.class,
    IndicatorType.class,
    ConsensusMethod.class,
    ExecutionOutcome.class,
    EventType.class,
    BackfillStatus.class,
    RecommendationType.class,
    ResearchOutcome.class,
    ResearchAgentType.class,
    ResearchStatus.class,

    // === Notifications ===
    com.quorumtrading.adapter.mcp.dto.McpNotificationDto.class,
    com.quorumtrading.adapter.mcp.dto.McpNotificationPreferenceDto.class,
    com.quorumtrading.model.entity.Notification.class,
    com.quorumtrading.model.entity.NotificationPreference.class,
    com.quorumtrading.model.valueobject.NotificationId.class,
    NotificationType.class,
    NotificationChannel.class,
    NotificationStatus.class,

    // === Contributions (recurring-investment top-ups) ===
    com.quorumtrading.adapter.mcp.dto.McpContributionDto.class,
    com.quorumtrading.model.entity.Contribution.class,
    com.quorumtrading.model.valueobject.ContributionId.class,

    // === Investment plans + allocations ===
    com.quorumtrading.adapter.mcp.dto.McpCostEstimateDto.class,
    com.quorumtrading.adapter.mcp.dto.McpInvestmentPlanDto.class,
    com.quorumtrading.adapter.mcp.dto.McpInvestmentPlanDto.Preferences.class,
    com.quorumtrading.adapter.mcp.dto.McpInvestmentAllocationDto.class,
    com.quorumtrading.model.entity.InvestmentPlan.class,
    com.quorumtrading.model.entity.InvestmentAllocation.class,
    com.quorumtrading.model.valueobject.InvestmentPlanId.class,
    com.quorumtrading.model.valueobject.InvestmentAllocationId.class,
    com.quorumtrading.model.valueobject.InvestmentPlanPreferences.class,
    com.quorumtrading.model.enums.BudgetMode.class,
    com.quorumtrading.model.enums.PickerPolicy.class,
    com.quorumtrading.model.enums.InvestmentPlanStatus.class,
    com.quorumtrading.model.enums.AllocationStatus.class,
    com.quorumtrading.model.enums.AllocationAction.class,
})
public class McpReflectionConfig {
}
