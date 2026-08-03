package com.quorumtrading.adapter.persistence;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.EmailSender;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.service.EmailTemplateRegistry;
import com.quorumtrading.application.service.UnsubscribeTokenService;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserBudgetId;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.Collections;
import java.util.List;

/**
 * Produces mock beans for persistence integration tests.
 * These mocks satisfy CDI dependencies without requiring full application bootstrap.
 */
@Mock
@ApplicationScoped
public class MockProducer {

    @Produces
    @ApplicationScoped
    public LLMPort mockLLMPort() {
        return new LLMPort() {
            @Override
            public LLMQueryResult queryWithUsage(String prompt, com.quorumtrading.application.orchestration.agents.LLMConfig config) {
                return LLMQueryResult.of("Mock LLM response", LLMUsage.ZERO, "mock-model");
            }

            @Override
            public String getProviderName() {
                return "MockProvider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<ResearchExecutionCompleted> mockResearchEventPublisher() {
        return event -> {
            // No-op event publisher for tests
        };
    }

    @Produces
    @ApplicationScoped
    public CreateSimulationUseCase mockCreateSimulationUseCase() {
        return new CreateSimulationUseCase() {
            @Override
            public SimulationId execute(CreateSimulationCommand command) {
                // No-op for adapter tests
                return new SimulationId(java.util.UUID.randomUUID());
            }
        };
    }

    @Produces
    @ApplicationScoped
    public GetSimulationChartDataUseCase mockGetSimulationChartDataUseCase() {
        return new GetSimulationChartDataUseCase() {
            @Override
            public java.util.List<com.quorumtrading.model.valueobject.ChartDataPoint> execute(GetSimulationChartDataQuery query) {
                // No-op for adapter tests
                return java.util.Collections.emptyList();
            }
        };
    }

    @Produces
    @ApplicationScoped
    public UserBudgetRepository userBudgetRepository() {
        return new UserBudgetRepository() {
            @Override
            public java.util.Optional<UserBudget> findByUserId(UserId userId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<UserBudget> findById(UserBudgetId id) {
                return java.util.Optional.empty();
            }

            @Override
            public UserBudget save(UserBudget userBudget) {
                return userBudget;
            }

            @Override
            public void delete(UserBudgetId id) {
                // No-op for adapter tests
            }

            @Override
            public boolean existsByUserId(UserId userId) {
                return false;
            }

            @Override
            public java.util.List<UserBudget> findAll() {
                return java.util.Collections.emptyList();
            }
        };
    }

    @Produces
    @ApplicationScoped
    public HistoricalPricePort historicalPricePort() {
        return new HistoricalPricePort() {
            @Override
            public List<PriceBar> getHistoricalPrices(Symbol ticker, int days) {
                return Collections.emptyList();
            }

            @Override
            public List<PriceBar> getHistoricalPrices(Symbol ticker, int days, java.time.Instant asOf) {
                return Collections.emptyList();
            }

            @Override
            public List<PriceBar> getHistoricalPrices(Symbol ticker, java.time.Instant from, java.time.Instant to) {
                return Collections.emptyList();
            }

            @Override
            public List<PriceBar> getHistoricalPrices(Symbol ticker, java.time.Instant from, java.time.Instant to, String resolution) {
                return Collections.emptyList();
            }

            @Override
            public com.quorumtrading.model.valueobject.PriceCoverage getCoverage(Symbol ticker) {
                return com.quorumtrading.model.valueobject.PriceCoverage.empty(ticker.ticker());
            }

            @Override
            public com.quorumtrading.model.valueobject.PriceCoverage getCoverage(
                    Symbol ticker, java.time.LocalDate from, java.time.LocalDate to) {
                return com.quorumtrading.model.valueobject.PriceCoverage.empty(ticker.ticker())
                    .withWindow(com.quorumtrading.model.valueobject.PriceCoverage.buildWindow(
                        from, to, ticker.type(), java.util.Set.of()));
            }
        };
    }

    /**
     * Mock {@link EmailSender}. The real adapter lives in the bootstrap module,
     * so persistence-adapter tests have no implementation on the classpath. The
     * mock no-ops "send" so {@link com.quorumtrading.adapter.persistence.dispatcher.EmailNotificationDispatcher}
     * can be constructed and unit-tested without bootstrapping Quarkus mail.
     */
    @Produces
    @ApplicationScoped
    public EmailSender mockEmailSender() {
        return new EmailSender() {
            @Override
            public Result send(EmailMessage message) {
                // Pretend the send succeeded for adapter tests.
                return Result.ok();
            }

            @Override
            public boolean isEnabled() {
                return false;
            }
        };
    }

    /**
     * Mock {@link EmailTemplateRegistry}. The class has no CDI scope (production
     * wiring lives in {@code ApplicationConfig.emailTemplateRegistry()}), so
     * tests need a producer to satisfy {@code EmailNotificationDispatcher}'s
     * injection point.
     */
    @Produces
    @ApplicationScoped
    public EmailTemplateRegistry mockEmailTemplateRegistry() {
        return new EmailTemplateRegistry();
    }

    /**
     * Mock {@link UnsubscribeTokenService}. The service requires an
     * unsubscribe-secret in its constructor; production wiring reads it from
     * config. Tests use a fixed dev secret so token mint/verify still round-trip.
     */
    @Produces
    @ApplicationScoped
    public UnsubscribeTokenService mockUnsubscribeTokenService() {
        return new UnsubscribeTokenService("test-only-unsubscribe-secret-1234567890");
    }

    @Produces
    @ApplicationScoped
    public IndicatorEnrichmentService indicatorEnrichmentService() {
        return new IndicatorEnrichmentService(null, null) {
            @Override
            public IndicatorContext calculateIndicators(List<Symbol> stockTickers) {
                return IndicatorContext.empty();
            }

            @Override
            public IndicatorContext calculateIndicators(List<Symbol> stockTickers, java.time.Instant asOf) {
                return IndicatorContext.empty();
            }
        };
    }

    /**
     * Mock {@link com.quorumtrading.application.service.DataHorizonService}. It's a plain
     * POJO wired in {@code ApplicationConfig} (bootstrap module), which isn't on the
     * persistence test classpath — so this producer satisfies {@code BackfillExecutor}'s
     * injection point during Quarkus deployment. Never invoked by persistence tests.
     */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.DataHorizonService dataHorizonService() {
        return new com.quorumtrading.application.service.DataHorizonService(null, null, 14);
    }

}
