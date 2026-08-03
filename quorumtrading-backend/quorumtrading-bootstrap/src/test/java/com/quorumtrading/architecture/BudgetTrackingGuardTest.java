package com.quorumtrading.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit guard ensuring every class that injects an LLM port also injects
 * {@code RecordLLMUsageUseCase} for budget tracking — unless explicitly allowlisted.
 *
 * <p>If this test fails, you added a new LLM consumer without budget tracking.
 * Either inject {@code RecordLLMUsageUseCase} and call it, or add the class to
 * the allowlist below <b>with justification</b>.</p>
 */
class BudgetTrackingGuardTest {

    private static JavaClasses classes;

    /**
     * Classes that use LLMPort/ConversationalLLMPort but delegate budget tracking
     * to their orchestrating caller. Each entry has a documented reason.
     */
    private static final Set<String> BUDGET_DELEGATION_ALLOWLIST = Set.of(
        // AI agents — budget tracked by ReachConsensusService which orchestrates all agents
        "TechnicalAgent",
        "FundamentalAgent",
        "SentimentAgent",
        "OrchestratorAgent",

        // Agent/orchestrator invocation component — budget pre-check stays in
        // ReachConsensusService; per-round usage is aggregated and recorded by
        // ConsensusOutcomeRecorder (which injects RecordLLMUsageUseCase)
        "AgentInvoker",

        // Market research orchestrator — budget tracked by ExecuteMarketResearchService
        "MarketResearchOrchestrator",

        // Strategy services — only call LLMPort.getModelInfo() for catalog data, no LLM generation
        "CreateStrategyService",
        "UpdateStrategyService",

        // Cost estimation — only calls LLMPort.getModelInfo() for pricing info, no LLM generation
        "EstimateBacktestCostUseCase",

        // Model config — only calls LLMPort.getAvailableModels()/getModelInfo(), no LLM generation
        "GetAvailableModelsUseCase",
        "UpdateModelPreferencesUseCase",

        // Model resolution — read the engine's catalogue to decide whether a resolved or pinned
        // model can be served, and to offer alternatives when it cannot. Neither ever issues a
        // query, so there is no usage to record; the agent that goes on to run the model does the
        // recording.
        "EngineCapabilityService",
        "ModelPinValidator",

        // Effective-model read model — sizes the engine's catalogue to report what will run. It
        // answers a question; it never asks the engine one, so there is no usage to record.
        "GetEffectiveModelsUseCase",

        // Chat sub-services — budget tracked by SendChatMessageUseCase which orchestrates them
        "ChatTitleGenerationService",
        "FollowUpSuggestionService"
    );

    private static final String LLM_PORT = "com.quorumtrading.application.port.out.LLMPort";
    private static final String CONVERSATIONAL_LLM_PORT = "com.quorumtrading.application.port.out.ConversationalLLMPort";
    private static final String RECORD_LLM_USAGE = "com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase";

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quorumtrading");
    }

    @Test
    void llmConsumersMustHaveBudgetTracking() {
        classes()
            .that().resideInAnyPackage(
                "com.quorumtrading.application.service..",
                "com.quorumtrading.application.usecase..",
                "com.quorumtrading.application.chat..",
                "com.quorumtrading.application.orchestration.agents.."
            )
            .should(new ArchCondition<>("have RecordLLMUsageUseCase if they use an LLM port") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    if (BUDGET_DELEGATION_ALLOWLIST.contains(javaClass.getSimpleName())) {
                        return; // Explicitly allowlisted — budget managed by orchestrating caller
                    }

                    boolean usesLLMPort = false;
                    boolean hasRecordUsage = false;

                    for (JavaField field : javaClass.getFields()) {
                        String fieldType = field.getRawType().getName();
                        if (fieldType.equals(LLM_PORT) || fieldType.equals(CONVERSATIONAL_LLM_PORT)) {
                            usesLLMPort = true;
                        }
                        if (fieldType.equals(RECORD_LLM_USAGE)) {
                            hasRecordUsage = true;
                        }
                    }

                    if (usesLLMPort && !hasRecordUsage) {
                        events.add(SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s injects an LLM port but does NOT inject RecordLLMUsageUseCase. " +
                                "Either add budget tracking or add it to BUDGET_DELEGATION_ALLOWLIST with justification.",
                                javaClass.getName()
                            )
                        ));
                    }
                }
            })
            .because("Every LLM consumer must track budget usage or be explicitly allowlisted with documented justification")
            .check(classes);
    }
}
