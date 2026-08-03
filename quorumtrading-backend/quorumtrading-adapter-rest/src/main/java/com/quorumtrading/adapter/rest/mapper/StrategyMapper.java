package com.quorumtrading.adapter.rest.mapper;

import com.quorumtrading.adapter.rest.dto.response.StrategyResponse;
import com.quorumtrading.model.entity.Strategy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Strategy domain objects and REST DTOs.
 */
@ApplicationScoped
public class StrategyMapper {

    /**
     * Convert domain Strategy to response DTO.
     */
    public StrategyResponse toResponse(Strategy strategy) {
        return new StrategyResponse(
            strategy.getId().value().toString(),
            strategy.getName(),
            strategy.getDescription(),
            strategy.getVersion(),
            strategy.getContent(),  // maps to markdownContent in response
            strategy.getCreatedAt(),
            strategy.getOwnerId() != null ? strategy.getOwnerId().value().toString() : null,
            strategy.isPublicVisible()
        );
    }

    /**
     * Convert list of domain Strategies to list of response DTOs.
     */
    public List<StrategyResponse> toResponseList(List<Strategy> strategies) {
        return strategies.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
}
