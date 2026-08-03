package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResearchAgentTypeTest {

    @Test
    void shouldHaveAllAgentTypes() {
        // Then
        assertThat(ResearchAgentType.values()).containsExactlyInAnyOrder(
            ResearchAgentType.NEWS_SENTIMENT_ANALYST,
            ResearchAgentType.STRATEGY_DESIGNER,
            ResearchAgentType.VIABILITY_ASSESSOR
        );
    }

    @Test
    void shouldHaveDescriptiveNames() {
        // Then
        assertThat(ResearchAgentType.NEWS_SENTIMENT_ANALYST.getDisplayName())
            .isEqualTo("News & Sentiment Analyst");
        assertThat(ResearchAgentType.STRATEGY_DESIGNER.getDisplayName())
            .isEqualTo("Strategy Designer");
        assertThat(ResearchAgentType.VIABILITY_ASSESSOR.getDisplayName())
            .isEqualTo("Viability Assessor");
    }
}
