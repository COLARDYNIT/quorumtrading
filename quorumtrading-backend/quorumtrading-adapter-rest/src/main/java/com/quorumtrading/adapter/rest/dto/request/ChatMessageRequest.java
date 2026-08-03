package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to send a chat message")
public record ChatMessageRequest(
    @NotBlank(message = "Message content is required")
    @Schema(description = "The user's message text", example = "List my strategies", required = true)
    String content
) {}
