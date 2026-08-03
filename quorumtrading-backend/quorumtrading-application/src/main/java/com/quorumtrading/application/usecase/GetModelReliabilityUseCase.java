package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.application.usecase.dto.ModelReliabilityStats;

/**
 * Returns aggregated model reliability statistics (success/failure counts).
 * No auth needed — this is platform-wide aggregate data with no user-specific info.
 */
public class GetModelReliabilityUseCase {

    private static final int DEFAULT_DAYS = 30;

    private final LlmParseFailureRepository llmParseFailureRepository;

    public GetModelReliabilityUseCase(LlmParseFailureRepository llmParseFailureRepository) {
        this.llmParseFailureRepository = llmParseFailureRepository;
    }

    public ModelReliabilityStats execute(int days) {
        if (days <= 0) {
            days = DEFAULT_DAYS;
        }
        return llmParseFailureRepository.getModelReliabilityStats(days);
    }
}
