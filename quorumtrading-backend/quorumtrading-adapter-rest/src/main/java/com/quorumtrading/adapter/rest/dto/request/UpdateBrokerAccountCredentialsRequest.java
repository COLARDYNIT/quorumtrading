package com.quorumtrading.adapter.rest.dto.request;

import com.quorumtrading.adapter.rest.dto.request.CreateBrokerAccountRequest.BrokerCredentialsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request DTO for updating broker account credentials and optionally the account name.
 */
@Schema(description = "Request to update broker account credentials")
public record UpdateBrokerAccountCredentialsRequest(

    @Schema(
        description = "New display name for the account. Null keeps the current name.",
        example = "My Updated Account",
        nullable = true
    )
    String name,

    @NotNull(message = "Credentials are required")
    @Valid
    @Schema(
        description = "New API credentials for the broker",
        required = true
    )
    BrokerCredentialsDto credentials

) {}
