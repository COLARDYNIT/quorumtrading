package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateBrokerAccountRequest;
import com.quorumtrading.adapter.rest.dto.request.CreateBrokerAccountRequest.BrokerCredentialsDto;
import com.quorumtrading.adapter.rest.dto.request.CreateBrokerAccountRequest.MockBrokerConfigDto;
import com.quorumtrading.adapter.rest.dto.request.UpdateBrokerAccountCredentialsRequest;
import com.quorumtrading.adapter.rest.dto.response.BrokerAccountResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.adapter.rest.exception.ConflictException;
import com.quorumtrading.adapter.rest.exception.NotFoundException;
import com.quorumtrading.adapter.rest.exception.UnprocessableEntityException;
import com.quorumtrading.adapter.rest.mapper.BrokerAccountMapper;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.exception.BrokerCredentialValidationException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.DeleteBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.UpdateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.application.usecase.command.DeleteBrokerAccountCommand;
import com.quorumtrading.application.usecase.command.UpdateBrokerAccountCredentialsCommand;
import com.quorumtrading.application.usecase.command.ValidateBrokerAccountCredentialsCommand;
import com.quorumtrading.application.usecase.query.GetBrokerAccountQuery;
import com.quorumtrading.application.usecase.query.ListBrokerAccountsQuery;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST resource for broker account management.
 * Provides endpoints for creating, listing, and managing broker connections.
 *
 * <p>This adapter delegates to application layer use cases following hexagonal architecture.</p>
 *
 * <p><b>Supported Broker Types:</b></p>
 * <ul>
 *   <li><b>MOCK:</b> Simulated broker for backtesting with configurable costs</li>
 *   <li><b>ALPACA_PAPER:</b> Alpaca paper trading account</li>
 *   <li><b>ALPACA_LIVE:</b> Alpaca live trading account</li>
 * </ul>
 */
@Path("/api/v1/broker-accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Broker Accounts", description = "Broker account connection and management")
public class BrokerAccountResource {

    @Inject
    CreateBrokerAccountUseCase createBrokerAccountUseCase;

    @Inject
    ListBrokerAccountsUseCase listBrokerAccountsUseCase;

    @Inject
    GetBrokerAccountUseCase getBrokerAccountUseCase;

    @Inject
    DeleteBrokerAccountUseCase deleteBrokerAccountUseCase;

    @Inject
    UpdateBrokerAccountCredentialsUseCase updateBrokerAccountCredentialsUseCase;

    @Inject
    ValidateBrokerAccountCredentialsUseCase validateBrokerAccountCredentialsUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    BrokerAccountMapper mapper;

    @POST
    @Operation(
        summary = "Create broker account",
        description = "Creates a new broker account connection. For MOCK accounts, provide mockConfig. " +
            "For real brokers (ALPACA_PAPER, ALPACA_LIVE), provide credentials which will be validated."
    )
    @APIResponse(
        responseCode = "201",
        description = "Broker account created successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BrokerAccountResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - validation failed or invalid configuration",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "422",
        description = "Unprocessable entity - credential validation failed",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response createBrokerAccount(
            @RequestBody(description = "Broker account creation request", required = true)
            @Valid CreateBrokerAccountRequest request,
            @Context UriInfo uriInfo) {

        // Get current authenticated user
        UserId userId = currentUser.getCurrentUserId();

        // Parse and validate broker type
        BrokerType brokerType;
        try {
            brokerType = BrokerType.valueOf(request.brokerType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid broker type: " + request.brokerType() +
                ". Supported values: MOCK, ALPACA_PAPER, ALPACA_LIVE");
        }

        // Validate request structure based on broker type
        validateRequestStructure(brokerType, request);

        // Build command
        CreateBrokerAccountCommand command = buildCreateCommand(userId, request.name(), brokerType, request);

        // Execute use case
        BrokerAccount brokerAccount;
        try {
            brokerAccount = createBrokerAccountUseCase.execute(command);
        } catch (BrokerCredentialValidationException e) {
            throw new UnprocessableEntityException(e.getMessage(), e);
        }

        // Build Location header
        URI location = uriInfo.getAbsolutePathBuilder()
            .path(brokerAccount.getId().value().toString())
            .build();

        return Response.created(location)
            .entity(mapper.toResponse(brokerAccount))
            .build();
    }

    @GET
    @Operation(
        summary = "List broker accounts",
        description = "Retrieves real broker accounts (ALPACA_PAPER, ALPACA_LIVE) belonging to the current user. " +
            "MOCK accounts are excluded from this list."
    )
    @APIResponse(
        responseCode = "200",
        description = "List of broker accounts retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                implementation = BrokerAccountResponse.class,
                type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY
            )
        )
    )
    public Response listBrokerAccounts() {
        // Get current authenticated user
        UserId userId = currentUser.getCurrentUserId();

        // Execute use case
        ListBrokerAccountsQuery query = new ListBrokerAccountsQuery(userId);
        List<BrokerAccount> brokerAccounts = listBrokerAccountsUseCase.execute(query);

        // Filter out MOCK accounts - only return real broker accounts (ALPACA_PAPER, ALPACA_LIVE)
        List<BrokerAccount> realBrokerAccounts = brokerAccounts.stream()
            .filter(account -> account.getBrokerType() != BrokerType.MOCK)
            .toList();

        return Response.ok(mapper.toResponseList(realBrokerAccounts)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Get broker account by ID",
        description = "Retrieves a specific broker account by its unique identifier"
    )
    @APIResponse(
        responseCode = "200",
        description = "Broker account retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BrokerAccountResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Broker account not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getBrokerAccount(
            @Parameter(description = "Broker account unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            BrokerAccountId brokerAccountId = new BrokerAccountId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            GetBrokerAccountQuery query = new GetBrokerAccountQuery(brokerAccountId, userId);
            BrokerAccount brokerAccount = getBrokerAccountUseCase.execute(query);

            return Response.ok(mapper.toResponse(brokerAccount)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid broker account ID format: " + id);
        } catch (EntityNotFoundException e) {
            throw new NotFoundException("Broker account not found with ID: " + id);
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(
        summary = "Delete broker account",
        description = "Deletes a broker account. Cannot delete an account with active simulations or reserved capital."
    )
    @APIResponse(
        responseCode = "204",
        description = "Broker account deleted successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "Broker account not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "409",
        description = "Conflict - cannot delete account with active simulations or reserved capital",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response deleteBrokerAccount(
            @Parameter(description = "Broker account unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            BrokerAccountId brokerAccountId = new BrokerAccountId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            DeleteBrokerAccountCommand command = new DeleteBrokerAccountCommand(brokerAccountId, userId);
            deleteBrokerAccountUseCase.execute(command);

            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid broker account ID format: " + id);
        } catch (EntityNotFoundException e) {
            throw new NotFoundException("Broker account not found with ID: " + id);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    @Operation(
        summary = "Update broker account credentials",
        description = "Updates the API credentials and optionally the display name of an existing broker account. " +
            "New credentials are validated against the broker API before persisting. " +
            "Cannot be used on MOCK accounts."
    )
    @APIResponse(
        responseCode = "200",
        description = "Credentials updated and validated successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BrokerAccountResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Broker account not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "409",
        description = "Conflict - cannot update credentials on a mock account",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "422",
        description = "Unprocessable entity - credential validation failed",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response updateBrokerAccountCredentials(
            @Parameter(description = "Broker account unique identifier", required = true)
            @PathParam("id") String id,
            @RequestBody(description = "Updated credentials", required = true)
            @Valid UpdateBrokerAccountCredentialsRequest request) {

        try {
            BrokerAccountId brokerAccountId = new BrokerAccountId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();

            BrokerCredentials credentials = new ApiKeyCredentials(
                request.credentials().apiKey(),
                request.credentials().apiSecret()
            );

            UpdateBrokerAccountCredentialsCommand command = new UpdateBrokerAccountCredentialsCommand(
                brokerAccountId,
                userId,
                request.name(),
                credentials
            );

            BrokerAccount brokerAccount = updateBrokerAccountCredentialsUseCase.execute(command);
            return Response.ok(mapper.toResponse(brokerAccount)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid broker account ID format: " + id);
        } catch (EntityNotFoundException e) {
            throw new NotFoundException("Broker account not found with ID: " + id);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        } catch (BrokerCredentialValidationException e) {
            throw new UnprocessableEntityException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/{id}/validate")
    @Operation(
        summary = "Re-validate broker credentials",
        description = "Re-validates the credentials for a broker account. " +
            "For MOCK accounts, this always succeeds. " +
            "For real brokers, this calls the broker API to verify credentials are still valid."
    )
    @APIResponse(
        responseCode = "200",
        description = "Credentials validated successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BrokerAccountResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Broker account not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "422",
        description = "Unprocessable entity - credential validation failed",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response validateCredentials(
            @Parameter(description = "Broker account unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            BrokerAccountId brokerAccountId = new BrokerAccountId(UUID.fromString(id));
            ValidateBrokerAccountCredentialsCommand command =
                new ValidateBrokerAccountCredentialsCommand(brokerAccountId, currentUser.getCurrentUserId());
            BrokerAccount brokerAccount = validateBrokerAccountCredentialsUseCase.execute(command);

            return Response.ok(mapper.toResponse(brokerAccount)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid broker account ID format: " + id);
        } catch (EntityNotFoundException e) {
            throw new NotFoundException("Broker account not found with ID: " + id);
        } catch (BrokerCredentialValidationException e) {
            throw new UnprocessableEntityException(e.getMessage(), e);
        }
    }

    /**
     * Validates the request structure based on broker type.
     */
    private void validateRequestStructure(BrokerType brokerType, CreateBrokerAccountRequest request) {
        if (brokerType == BrokerType.MOCK) {
            if (request.mockConfig() == null) {
                throw new BadRequestException("mockConfig is required for MOCK broker type");
            }
            if (request.credentials() != null) {
                throw new BadRequestException("credentials must be null for MOCK broker type");
            }
        } else {
            // Real broker
            if (request.credentials() == null) {
                throw new BadRequestException("credentials are required for " + brokerType + " broker type");
            }
            if (request.mockConfig() != null) {
                throw new BadRequestException("mockConfig must be null for " + brokerType + " broker type");
            }
        }
    }

    /**
     * Builds the CreateBrokerAccountCommand from the request.
     */
    private CreateBrokerAccountCommand buildCreateCommand(
            UserId userId,
            String name,
            BrokerType brokerType,
            CreateBrokerAccountRequest request) {

        if (brokerType == BrokerType.MOCK) {
            MockBrokerConfig mockConfig = buildMockConfig(request.mockConfig());
            return CreateBrokerAccountCommand.forMock(userId, name, mockConfig);
        } else {
            BrokerCredentials credentials = buildCredentials(request.credentials());
            if (brokerType == BrokerType.ALPACA_PAPER) {
                return CreateBrokerAccountCommand.forAlpacaPaper(userId, name, credentials);
            } else {
                return CreateBrokerAccountCommand.forAlpacaLive(userId, name, credentials);
            }
        }
    }

    /**
     * Builds MockBrokerConfig from the DTO.
     */
    private MockBrokerConfig buildMockConfig(MockBrokerConfigDto dto) {
        Money initialCapital = new Money(dto.initialCapital(), Money.Currency.USD);

        // If preset is provided (and not CUSTOM), use it
        if (dto.preset() != null && !dto.preset().isEmpty() && !dto.preset().equals("CUSTOM")) {
            try {
                CostPreset preset = CostPreset.valueOf(dto.preset());
                return MockBrokerConfig.fromPreset(initialCapital, preset);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid cost preset: " + dto.preset() +
                    ". Supported values: ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION");
            }
        }

        // Custom configuration - validate all required fields
        if (dto.commissionRate() == null || dto.cryptoCommissionRate() == null ||
            dto.slippageBase() == null ||
            dto.slippageImpactPerUnit() == null || dto.slippageVolatilityMultiplier() == null) {
            throw new BadRequestException(
                "When preset is not provided or is CUSTOM, all cost parameters are required: " +
                "commissionRate, cryptoCommissionRate, slippageBase, slippageImpactPerUnit, slippageVolatilityMultiplier"
            );
        }

        return MockBrokerConfig.custom(
            initialCapital,
            dto.commissionRate(),
            dto.cryptoCommissionRate(),
            dto.slippageBase(),
            dto.slippageImpactPerUnit(),
            dto.slippageVolatilityMultiplier()
        );
    }

    /**
     * Builds BrokerCredentials from the DTO.
     */
    private BrokerCredentials buildCredentials(BrokerCredentialsDto dto) {
        return new ApiKeyCredentials(dto.apiKey(), dto.apiSecret());
    }
}
