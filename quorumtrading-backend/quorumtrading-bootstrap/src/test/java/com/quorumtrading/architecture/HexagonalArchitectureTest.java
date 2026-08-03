package com.quorumtrading.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit tests to enforce hexagonal architecture boundaries.
 * These tests ensure that:
 * - Domain layer has zero infrastructure dependencies
 * - Application layer only depends on domain
 * - Adapters are independent and only depend on application/domain
 * - Port interfaces are properly defined and implemented
 */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quorumtrading");
    }

    @Test
    void domainModelShouldNotDependOnAnyInfrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quorumtrading.model..")
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.quorumtrading.application..",
                    "com.quorumtrading.adapter..",
                    "com.quorumtrading.bootstrap..",
                    "jakarta.persistence..",
                    "jakarta.ws.rs..",
                    "jakarta.enterprise..",
                    "com.fasterxml.jackson..",
                    "io.quarkus.."
                )
            .because("Domain model must be pure and have zero infrastructure dependencies");

        rule.check(classes);
    }

    /**
     * Known CDI-coupled application classes, exempted by exact fully-qualified name
     * (not suffix wildcards) so new classes cannot silently inherit the exemption:
     * - BrokerRouter: infrastructure service using CDI (@Named lookup)
     * - BackfillExecutor: needs Instance&lt;MarketDataPort&gt; and Event
     * - InMemoryRateLimitTracker: stateful singleton
     * - RequestBackfillService: needs Event for immediate WebSocket notification
     */
    private static final String[] CDI_EXEMPT_APPLICATION_CLASSES = {
        "com.quorumtrading.application.service.BrokerRouter",
        "com.quorumtrading.application.service.BackfillExecutor",
        "com.quorumtrading.application.service.InMemoryRateLimitTracker",
        "com.quorumtrading.application.service.RequestBackfillService"
    };

    @Test
    void applicationLayerShouldOnlyDependOnDomain() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quorumtrading.application..")
            .and().doNotHaveFullyQualifiedName(CDI_EXEMPT_APPLICATION_CLASSES[0])
            .and().doNotHaveFullyQualifiedName(CDI_EXEMPT_APPLICATION_CLASSES[1])
            .and().doNotHaveFullyQualifiedName(CDI_EXEMPT_APPLICATION_CLASSES[2])
            .and().doNotHaveFullyQualifiedName(CDI_EXEMPT_APPLICATION_CLASSES[3])
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.quorumtrading.adapter..",
                    "com.quorumtrading.bootstrap..",
                    "jakarta.persistence..",
                    "jakarta.ws.rs..",
                    "jakarta.enterprise..",
                    "jakarta.inject..",
                    "io.quarkus.."
                )
            .because("Application layer should only depend on domain model and standard Java libraries "
                + "(application services are POJOs wired in ApplicationConfig, not CDI beans)");

        rule.check(classes);
    }

    /**
     * Every adapter module's root package. One entry per {@code quorumtrading-adapter-*} module;
     * adding a module without adding it here silently exempts it from isolation.
     */
    private static final String[] ADAPTER_ROOT_PACKAGES = {
        "com.quorumtrading.adapter.broker.alpaca",
        "com.quorumtrading.adapter.broker.mock",
        "com.quorumtrading.adapter.broker.saxo",
        "com.quorumtrading.adapter.featureflag.unleash",
        "com.quorumtrading.adapter.llm.local",
        "com.quorumtrading.adapter.llm.openrouter",
        "com.quorumtrading.adapter.llm.router",
        "com.quorumtrading.adapter.marketdata.alpaca",
        "com.quorumtrading.adapter.marketdata.coinbase",
        "com.quorumtrading.adapter.marketdata.finnhub",
        "com.quorumtrading.adapter.marketdata.kraken",
        "com.quorumtrading.adapter.marketdata.reddit",
        "com.quorumtrading.adapter.marketdata.rss",
        "com.quorumtrading.adapter.marketdata.saxo",
        "com.quorumtrading.adapter.marketdata.tiingo",
        "com.quorumtrading.adapter.mcp",
        "com.quorumtrading.adapter.payment.stripe",
        "com.quorumtrading.adapter.persistence",
        "com.quorumtrading.adapter.redis",
        "com.quorumtrading.adapter.rest",
        "com.quorumtrading.adapter.websocket"
    };

    /**
     * No adapter may reach into another adapter — for every module, not just the handful that
     * happened to be listed. Adapters are interchangeable implementations behind ports; one
     * calling another turns a swap into a cascade.
     */
    @Test
    void noAdapterMayDependOnAnyOtherAdapter() {
        for (String self : ADAPTER_ROOT_PACKAGES) {
            String[] others = java.util.Arrays.stream(ADAPTER_ROOT_PACKAGES)
                .filter(other -> !other.equals(self))
                // broker.saxo and marketdata.saxo are distinct modules but neither is a prefix
                // of the other, so plain inequality is enough to keep a package out of its own
                // "others" list.
                .map(other -> other + "..")
                .toArray(String[]::new);

            noClasses()
                .that().resideInAPackage(self + "..")
                .should().dependOnClassesThat().resideInAnyPackage(others)
                .because(self + " must depend only on the application and domain layers")
                .check(classes);
        }
    }

    @Test
    void adaptersShouldNotDependOnEachOther() {
        ArchRule llmAdapterRule = noClasses()
            .that().resideInAPackage("com.quorumtrading.adapter.llm..")
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.quorumtrading.adapter.persistence..",
                    "com.quorumtrading.adapter.rest..",
                    "com.quorumtrading.adapter.websocket..",
                    "com.quorumtrading.adapter.marketdata.."
                )
            .because("LLM adapters should be independent and only depend on application/domain");

        ArchRule persistenceAdapterRule = noClasses()
            .that().resideInAPackage("com.quorumtrading.adapter.persistence..")
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.quorumtrading.adapter.llm..",
                    "com.quorumtrading.adapter.rest..",
                    "com.quorumtrading.adapter.websocket..",
                    "com.quorumtrading.adapter.marketdata.."
                )
            .because("Persistence adapters should be independent and only depend on application/domain");

        ArchRule restAdapterRule = noClasses()
            .that().resideInAPackage("com.quorumtrading.adapter.rest..")
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.quorumtrading.adapter.llm..",
                    "com.quorumtrading.adapter.persistence..",
                    "com.quorumtrading.adapter.websocket..",
                    "com.quorumtrading.adapter.marketdata.."
                )
            .because("REST adapters should be independent and only depend on application/domain");

        ArchRule brokerSaxoAdapterRule = noClasses()
            .that().resideInAPackage("com.quorumtrading.adapter.broker.saxo..")
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.quorumtrading.adapter.llm..",
                    "com.quorumtrading.adapter.persistence..",
                    "com.quorumtrading.adapter.rest..",
                    "com.quorumtrading.adapter.websocket..",
                    "com.quorumtrading.adapter.marketdata..",
                    "com.quorumtrading.adapter.broker.alpaca..",
                    "com.quorumtrading.adapter.broker.mock.."
                )
            .because("Saxo broker adapter should be independent and only depend on application/domain");

        ArchRule marketDataSaxoAdapterRule = noClasses()
            .that().resideInAPackage("com.quorumtrading.adapter.marketdata.saxo..")
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.quorumtrading.adapter.llm..",
                    "com.quorumtrading.adapter.persistence..",
                    "com.quorumtrading.adapter.rest..",
                    "com.quorumtrading.adapter.websocket..",
                    "com.quorumtrading.adapter.marketdata.finnhub..",
                    "com.quorumtrading.adapter.marketdata.alpaca..",
                    "com.quorumtrading.adapter.marketdata.tiingo..",
                    "com.quorumtrading.adapter.marketdata.reddit..",
                    "com.quorumtrading.adapter.marketdata.rss..",
                    "com.quorumtrading.adapter.broker.."
                )
            .because("Saxo market-data adapter should be independent and only depend on application/domain");

        llmAdapterRule.check(classes);
        persistenceAdapterRule.check(classes);
        restAdapterRule.check(classes);
        brokerSaxoAdapterRule.check(classes);
        marketDataSaxoAdapterRule.check(classes);
    }

    @Test
    void portInterfacesShouldBeInApplicationLayer() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Port")
            .or().haveSimpleNameEndingWith("Repository")
            .and().areInterfaces()
            .should().resideInAPackage("com.quorumtrading.application.port..")
            .because("All port interfaces must be in application layer");

        rule.check(classes);
    }

    @Test
    void adapterImplementationsShouldBeInAdapterPackages() {
        ArchRule restAdapterRule = classes()
            .that().haveSimpleNameEndingWith("Resource")
            .should().resideInAPackage("com.quorumtrading.adapter.rest..")
            .because("REST resources are adapters and must be in adapter package");

        ArchRule repositoryImplRule = classes()
            .that().haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAPackage("com.quorumtrading.adapter.persistence..")
            .because("Repository implementations are adapters and must be in adapter package");

        restAdapterRule.check(classes);
        repositoryImplRule.check(classes);
    }

    @Test
    void layeredArchitectureIsRespected() {
        ArchRule rule = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("com.quorumtrading.model..")
            .layer("Application").definedBy("com.quorumtrading.application..")
            .layer("Adapters").definedBy("com.quorumtrading.adapter..")
            .layer("Bootstrap").definedBy("com.quorumtrading.bootstrap..")

            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Adapters").mayOnlyAccessLayers("Application", "Domain")
            .whereLayer("Bootstrap").mayOnlyAccessLayers("Adapters", "Application", "Domain")

            .because("Hexagonal architecture requires strict layer dependencies: Domain → Application → Adapters → Bootstrap");

        rule.check(classes);
    }

    // ------------------------------------------------------------------
    // Frozen legacy violations
    //
    // The three rules below describe where the architecture is meant to be, and each carries an
    // exhaustive list of the classes that predate it. The lists are exact fully-qualified names,
    // never wildcards, so a new class cannot silently inherit an exemption — it fails the build
    // until someone either fixes it or argues it onto the list in review. Entries are meant to
    // be deleted over time; none should ever be added without a reason in the commit message.
    // ------------------------------------------------------------------

    private static DescribedPredicate<JavaClass> notFrozen(String reason, Set<String> frozen) {
        return new DescribedPredicate<>("not one of the %d frozen %s".formatted(frozen.size(), reason)) {
            @Override
            public boolean test(JavaClass javaClass) {
                String topLevelName = javaClass.getName().split("\\$", 2)[0];
                return !frozen.contains(topLevelName);
            }
        };
    }

    /**
     * Driving adapters that still query output ports themselves instead of going through a use
     * case. This is how the challenge leaderboard came to report two different return
     * percentages: REST and MCP each assembled it from repositories, and drifted apart.
     */
    private static final Set<String> DRIVING_ADAPTERS_USING_OUTPUT_PORTS = Set.of(
        "com.quorumtrading.adapter.mcp.tool.BenchmarkTools",
        "com.quorumtrading.adapter.mcp.tool.ChallengeTools",
        "com.quorumtrading.adapter.mcp.tool.ConfigTools",
        "com.quorumtrading.adapter.mcp.tool.InvestmentPlanTools",
        "com.quorumtrading.adapter.mcp.tool.MarketDataTools",
        "com.quorumtrading.adapter.mcp.tool.MarketResearchTools",
        "com.quorumtrading.adapter.mcp.tool.SimulationTools",
        "com.quorumtrading.adapter.mcp.tool.StrategyTools",
        "com.quorumtrading.adapter.rest.mapper.SimulationMapper",
        "com.quorumtrading.adapter.rest.resource.AdminResource",
        "com.quorumtrading.adapter.rest.resource.AuthResource",
        "com.quorumtrading.adapter.rest.resource.BenchmarkResource",
        "com.quorumtrading.adapter.rest.resource.BudgetResource",
        "com.quorumtrading.adapter.rest.resource.ChallengeResource",
        "com.quorumtrading.adapter.rest.resource.ChatResource",
        "com.quorumtrading.adapter.rest.resource.DataAvailabilityResource",
        "com.quorumtrading.adapter.rest.resource.MarketDataResource",
        "com.quorumtrading.adapter.rest.resource.PaymentResource",
        "com.quorumtrading.adapter.rest.resource.SimulationResource",
        "com.quorumtrading.adapter.rest.resource.StrategyResource",
        "com.quorumtrading.adapter.rest.security.CurrentUserProvider",
        "com.quorumtrading.adapter.websocket.BackfillProgressBroadcaster",
        "com.quorumtrading.adapter.websocket.ResearchProgressBroadcaster",
        "com.quorumtrading.adapter.websocket.StreamEndpoint"
    );

    @Test
    void drivingAdaptersShouldGoThroughUseCasesNotOutputPorts() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.quorumtrading.adapter.rest..",
                "com.quorumtrading.adapter.mcp..",
                "com.quorumtrading.adapter.websocket..")
            .and(notFrozen("driving adapters", DRIVING_ADAPTERS_USING_OUTPUT_PORTS))
            .should().dependOnClassesThat().resideInAPackage("com.quorumtrading.application.port.out..")
            .because("driving adapters translate transport to use cases; reaching for an output "
                + "port puts orchestration in the adapter, where the next adapter reimplements "
                + "it differently");

        rule.check(classes);
    }

    /**
     * Application classes still expressed in a framework's types. Jackson annotations pin a wire
     * format onto an application type; Mutiny and SmallRye pin a runtime onto it.
     */
    private static final Set<String> APPLICATION_CLASSES_WITH_FRAMEWORK_TYPES = Set.of(
        "com.quorumtrading.application.chat.ChatSession",
        "com.quorumtrading.application.chat.ChatStreamEvent",
        "com.quorumtrading.application.chat.ChatToolExecutor",
        "com.quorumtrading.application.chat.FollowUpSuggestionService",
        "com.quorumtrading.application.chat.SendChatMessageUseCase",
        "com.quorumtrading.application.config.AutoBacktestConfig",
        "com.quorumtrading.application.config.TradingLimitsConfig",
        "com.quorumtrading.application.orchestration.agents.AgentResponseParser",
        "com.quorumtrading.application.orchestration.agents.ConsensusActionParser",
        "com.quorumtrading.application.orchestration.agents.OrchestratorAgent",
        "com.quorumtrading.application.orchestration.agents.TechnicalAgent",
        "com.quorumtrading.application.service.EvaluatePortfolioValueService",
        "com.quorumtrading.application.service.FindTickerService",
        "com.quorumtrading.application.service.LlmStrategyPicker",
        "com.quorumtrading.application.service.MarketResearchOrchestrator",
        "com.quorumtrading.application.service.ReachConsensusService"
    );

    @Test
    void applicationLayerShouldNotDependOnSerializationOrRuntimeFrameworks() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quorumtrading.application..")
            .and(notFrozen("application classes", APPLICATION_CLASSES_WITH_FRAMEWORK_TYPES))
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.fasterxml.jackson..",
                    "io.smallrye..",
                    "io.micrometer..")
            .because("the application layer states what the system does, not how it is serialised "
                + "or which reactive runtime carries it");

        rule.check(classes);
    }

    /**
     * Domain entities that still expose public setters. A setter next to a state-machine method
     * is an unguarded second way to change the same field: {@code Simulation} has
     * {@code start()}, {@code complete()} and {@code fail()} and also a {@code setStatus()} that
     * skips them.
     */
    private static final Set<String> ENTITIES_WITH_PUBLIC_SETTERS = Set.of(
        "com.quorumtrading.model.entity.ConsensusDecision",
        "com.quorumtrading.model.entity.LLMDecision",
        "com.quorumtrading.model.entity.NotificationPreference",
        "com.quorumtrading.model.entity.PaymentTransaction",
        "com.quorumtrading.model.entity.PortfolioSnapshot",
        "com.quorumtrading.model.entity.ResearchExecution",
        "com.quorumtrading.model.entity.ScheduledSimulation",
        "com.quorumtrading.model.entity.Simulation",
        "com.quorumtrading.model.entity.Strategy",
        "com.quorumtrading.model.entity.UserBudget"
    );

    @Test
    void domainEntitiesShouldNotExposePublicSetters() {
        ArchRule rule = noMethods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().resideInAPackage("com.quorumtrading.model.entity..")
            .and().areDeclaredInClassesThat(
                notFrozen("entities", ENTITIES_WITH_PUBLIC_SETTERS))
            .should().haveNameMatching("set[A-Z].*")
            .because("an entity changes state through methods that name the transition and "
                + "enforce its invariants; persistence reconstitutes through a factory");

        rule.check(classes);
    }
}
