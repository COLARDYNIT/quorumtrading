package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to update the chat session's LLM model")
public record UpdateChatModelRequest(
    @NotBlank(message = "Model ID is required")
    @Schema(description = "The LLM model ID", example = "mistralai/mistral-small-3.2-24b-instruct", required = true)
    String model
) {}
