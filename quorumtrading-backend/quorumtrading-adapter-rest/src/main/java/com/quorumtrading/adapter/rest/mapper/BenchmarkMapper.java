package com.quorumtrading.adapter.rest.mapper;

import com.quorumtrading.adapter.rest.dto.request.CreateBenchmarkRequest;
import com.quorumtrading.adapter.rest.dto.response.CreateBenchmarkResponse;
import com.quorumtrading.adapter.rest.dto.response.BenchmarkResponse;
import com.quorumtrading.application.usecase.command.CreateBenchmarkCommand;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Benchmark domain objects and REST DTOs.
 */
@ApplicationScoped
public class BenchmarkMapper {

    public CreateBenchmarkCommand toCommand(CreateBenchmarkRequest request, UserId userId) {
        List<UUID> strategyUuids = request.strategyIds().stream()
            .map(UUID::fromString)
            .collect(Collectors.toList());

        Instant effectiveStartDate = request.startDate() != null
            ? request.startDate()
            : Instant.now();

        MockBrokerConfig mockConfig = null;
        if (request.mockConfig() != null) {
            mockConfig = toMockBrokerConfig(request.mockConfig());
        }

        BrokerAccountId brokerAccountId = null;
        if (request.brokerAccountId() != null && !request.brokerAccountId().isBlank()) {
            brokerAccountId = new BrokerAccountId(UUID.fromString(request.brokerAccountId()));
        }

        Money capitalPerStrategy = null;
        if (request.capitalPerStrategy() != null) {
            capitalPerStrategy = new Money(request.capitalPerStrategy(), Money.Currency.USD);
        }

        return new CreateBenchmarkCommand(
            strategyUuids,
            effectiveStartDate,
            request.endDate(),
            request.name(),
            mockConfig,
            userId,
            brokerAccountId,
            capitalPerStrategy
        );
    }

    private MockBrokerConfig toMockBrokerConfig(CreateBenchmarkRequest.MockBrokerConfigDto dto) {
        BigDecimal initialCapital = dto.initialCapital() != null
            ? dto.initialCapital()
            : new BigDecimal("100000.00");

        String presetStr = dto.preset() != null && !dto.preset().isBlank()
            ? dto.preset()
            : "TYPICAL_RETAIL";

        Money capital = new Money(initialCapital, Money.Currency.USD);

        CostPreset preset;
        try {
            preset = CostPreset.valueOf(presetStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cost preset: " + presetStr +
                ". Supported values: ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION");
        }

        return MockBrokerConfig.fromPreset(capital, preset);
    }

    public CreateBenchmarkResponse toCreateResponseDTO(
        com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse response
    ) {
        return new CreateBenchmarkResponse(
            response.benchmarkId() != null ? response.benchmarkId().toString() : null,
            response.simulationIds().stream()
                .map(UUID::toString)
                .collect(Collectors.toList()),
            response.benchmarkName()
        );
    }

    public BenchmarkResponse toResponseDTO(Benchmark benchmark, List<Simulation> simulations) {
        return new BenchmarkResponse(
            benchmark.getBenchmarkId().value().toString(),
            benchmark.getName(),
            benchmark.getStartDate(),
            benchmark.getEndDate(),
            simulations.stream()
                .map(sim -> sim.getSimulationId().value().toString())
                .collect(Collectors.toList()),
            benchmark.getCreatedAt(),
            benchmark.isLive()
        );
    }

    public List<BenchmarkResponse> toResponseDTOList(List<Benchmark> benchmarks) {
        return benchmarks.stream()
            .map(b -> new BenchmarkResponse(
                b.getBenchmarkId().value().toString(),
                b.getName(),
                b.getStartDate(),
                b.getEndDate(),
                List.of(),
                b.getCreatedAt(),
                b.isLive()
            ))
            .collect(Collectors.toList());
    }
}
