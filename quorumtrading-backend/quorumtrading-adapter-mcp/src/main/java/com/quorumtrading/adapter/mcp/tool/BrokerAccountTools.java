package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpBrokerAccountDto;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.command.ValidateBrokerAccountCredentialsCommand;
import com.quorumtrading.application.usecase.query.GetBrokerAccountQuery;
import com.quorumtrading.application.usecase.query.ListBrokerAccountsQuery;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP tools for broker account management.
 * Exposes list, get, and validate operations.
 * NOTE: No create operation — credentials must never be transmitted through MCP.
 */
@Singleton
@ActivateRequestContext
public class BrokerAccountTools extends McpToolBase {

    @Inject
    ListBrokerAccountsUseCase listBrokerAccountsUseCase;

    @Inject
    GetBrokerAccountUseCase getBrokerAccountUseCase;

    @Inject
    ValidateBrokerAccountCredentialsUseCase validateBrokerAccountCredentialsUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @Inject
    McpDtoMapper mapper;

    @Tool(name = "list_broker_accounts", description = "List all broker accounts for the current user. "
        + "Returns account details including balance, status, and type. Credentials are never exposed. "
        + "Use 'brokerType' to filter by type: MOCK, ALPACA_PAPER, ALPACA_LIVE, SAXO_SIM or SAXO_LIVE. "
        + "IMPORTANT: this includes MOCK accounts, which are created automatically for backtests and "
        + "cannot place an order — the web UI hides them, so this count and the UI's 'Connected "
        + "brokers' figure legitimately differ. To answer 'do I have a broker set up?', read "
        + "tradeableCount (non-MOCK accounts), not count. "
        + "Only ALPACA_LIVE and SAXO_LIVE trade real money.")
    public ToolResponse listBrokerAccounts(
        @ToolArg(description = "Optional filter by broker type: MOCK, ALPACA_PAPER, ALPACA_LIVE, SAXO_SIM or SAXO_LIVE") Optional<String> brokerType
    ) {
        return execute(() -> {
            ListBrokerAccountsQuery query = new ListBrokerAccountsQuery(mcpUserContext.getUserId());
            List<BrokerAccount> accounts = listBrokerAccountsUseCase.execute(query);

            // Apply broker type filter if specified
            if (brokerType.isPresent() && !brokerType.get().isBlank()) {
                BrokerType typeFilter = parseEnum(BrokerType.class, brokerType.get(), "brokerType");
                accounts = accounts.stream()
                    .filter(a -> a.getBrokerType() == typeFilter)
                    .toList();
            }

            List<McpBrokerAccountDto> dtos = accounts.stream()
                .map(mapper::toBrokerAccountDto)
                .toList();

            // Backtests get a mock account created for them automatically, so this list can hold
            // dozens of MOCK rows all reading status ACTIVE while the web UI — which excludes them —
            // correctly says "Connected brokers: 0". Asked "do I have a broker set up?", the raw
            // count answered "yes, 16 active accounts" when the truthful answer was zero, and the
            // live-trading walkthrough then proceeded on that. Counting the tradeable ones here
            // makes the honest number impossible to miss.
            long tradeable = accounts.stream().filter(a -> a.getBrokerType() != BrokerType.MOCK).count();
            var body = new java.util.LinkedHashMap<String, Object>();
            body.put("items", dtos);
            body.put("count", dtos.size());
            body.put("hasMore", false);
            body.put("tradeableCount", tradeable);
            if (tradeable == 0 && !dtos.isEmpty()) {
                body.put("note", "Every account here is MOCK — created automatically for backtests, "
                    + "and unable to place an order. You have no broker connected. Add one in the "
                    + "web UI at /settings/broker-accounts; credentials cannot be passed through MCP.");
            }
            return toJson(body);
        });
    }

    @Tool(name = "get_broker_account", description = "Get detailed information about a specific broker account by its ID. Returns balance, status, and broker type. Credentials are never exposed.")
    public ToolResponse getBrokerAccount(
        @ToolArg(description = "UUID of the broker account") String brokerAccountId
    ) {
        return execute(() -> {
            BrokerAccountId id = new BrokerAccountId(UUID.fromString(brokerAccountId));
            BrokerAccount account = getBrokerAccountUseCase.execute(new GetBrokerAccountQuery(id, mcpUserContext.getUserId()));
            return toJson(mapper.toBrokerAccountDto(account));
        });
    }

    @Tool(name = "validate_broker_credentials", description = "Validate the credentials of a broker account by testing the connection with the broker API. For mock accounts, validation always succeeds. Returns the updated account status.")
    public ToolResponse validateBrokerCredentials(
        @ToolArg(description = "UUID of the broker account to validate") String brokerAccountId
    ) {
        return execute(() -> {
            BrokerAccountId id = new BrokerAccountId(UUID.fromString(brokerAccountId));
            BrokerAccount account = validateBrokerAccountCredentialsUseCase.execute(
                // Resolving the user both requires a token (/mcp is open at the HTTP layer) and
                // authorizes the probe — this reaches the broker API with stored credentials.
                new ValidateBrokerAccountCredentialsCommand(id, mcpUserContext.getUserId())
            );
            logger.info("MCP: Validated broker account {} (status={})", brokerAccountId, account.getStatus());
            return toJson(mapper.toBrokerAccountDto(account));
        });
    }
}
