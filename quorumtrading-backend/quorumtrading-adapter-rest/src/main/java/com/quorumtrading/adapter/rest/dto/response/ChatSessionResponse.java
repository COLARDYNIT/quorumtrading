package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.valueobject.FollowUpSuggestion;
import com.quorumtrading.model.valueobject.LLMUsage;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Chat session details")
public record ChatSessionResponse(
    @Schema(description = "Session ID") UUID sessionId,
    @Schema(description = "LLM-generated greeting") String greeting,
    @Schema(description = "Current LLM model") String model,
    @Schema(description = "Message history") List<MessageDto> messages,
    @Schema(description = "Cumulative token usage") UsageDto cumulativeUsage,
    @Schema(description = "Follow-up suggestions") List<FollowUpSuggestion> suggestions,
    @Schema(description = "Session title (auto-generated)") String title
) {

    @Schema(description = "A single chat message")
    public record MessageDto(
        @Schema(description = "Message role: user, assistant, system") String role,
        @Schema(description = "Message content") String content
    ) {}

    @Schema(description = "Token usage summary")
    public record UsageDto(
        @Schema(description = "Prompt tokens used") int promptTokens,
        @Schema(description = "Completion tokens used") int completionTokens,
        @Schema(description = "Total tokens used") int totalTokens,
        @Schema(description = "Billed cost in USD") String billedCost
    ) {
        public static UsageDto from(LLMUsage usage) {
            return new UsageDto(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.billedCost().toPlainString()
            );
        }
    }
}
