package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.command.ValidateBrokerAccountCredentialsCommand;
import com.quorumtrading.application.usecase.query.GetBrokerAccountQuery;
import com.quorumtrading.application.usecase.query.ListBrokerAccountsQuery;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class BrokerAccountToolsTest {

    @Inject
    BrokerAccountTools brokerAccountTools;

    @InjectMock
    ListBrokerAccountsUseCase listBrokerAccountsUseCase;

    @InjectMock
    GetBrokerAccountUseCase getBrokerAccountUseCase;

    @InjectMock
    ValidateBrokerAccountCredentialsUseCase validateBrokerAccountCredentialsUseCase;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID ACCOUNT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Test
    void listBrokerAccounts_returnsAccounts() throws Exception {
        BrokerAccount account = createTestMockAccount();
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(listBrokerAccountsUseCase.execute(any(ListBrokerAccountsQuery.class)))
            .thenReturn(List.of(account));

        ToolResponse response = brokerAccountTools.listBrokerAccounts(Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("hasMore").asBoolean()).isFalse();

        JsonNode item = json.get("items").get(0);
        assertThat(item.get("name").asText()).isEqualTo("Mock Account");
        assertThat(item.get("brokerType").asText()).isEqualTo("MOCK");
        assertThat(item.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void listBrokerAccounts_returnsAuthErrorWhenNoToken() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = brokerAccountTools.listBrokerAccounts(Optional.empty());

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Authentication required");
        assertThat(errorText).contains("quorumtrading.com/settings/api-tokens");
        assertThat(errorText).contains("claude mcp add");
    }

    @Test
    void getBrokerAccount_returnsAccountDetails() throws Exception {
        BrokerAccount account = createTestMockAccount();
        when(getBrokerAccountUseCase.execute(any(GetBrokerAccountQuery.class)))
            .thenReturn(account);

        ToolResponse response = brokerAccountTools.getBrokerAccount(ACCOUNT_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isNotBlank();
        assertThat(json.get("name").asText()).isEqualTo("Mock Account");
        assertThat(json.get("brokerType").asText()).isEqualTo("MOCK");
        assertThat(json.get("totalBalance").asText()).isEqualTo("100000");
    }

    @Test
    void validateBrokerCredentials_returnsUpdatedAccount() throws Exception {
        BrokerAccount account = createTestMockAccount();
        when(validateBrokerAccountCredentialsUseCase.execute(any(ValidateBrokerAccountCredentialsCommand.class)))
            .thenReturn(account);

        ToolResponse response = brokerAccountTools.validateBrokerCredentials(ACCOUNT_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isNotBlank();
        assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void getBrokerAccount_returnsErrorForInvalidUuid() {
        ToolResponse response = brokerAccountTools.getBrokerAccount("not-a-uuid");

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid UUID string");
    }

    @Test
    void listBrokerAccounts_filtersByBrokerType() throws Exception {
        BrokerAccount mockAccount = createTestMockAccount();
        // Create a second mock account (both are MOCK type)
        BrokerAccount mockAccount2 = BrokerAccount.createMock(
            new UserId(USER_ID), "Mock Account 2",
            MockBrokerConfig.fromPreset(Money.usd(new BigDecimal("50000")), com.quorumtrading.model.enums.CostPreset.ZERO_FEES)
        );

        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(listBrokerAccountsUseCase.execute(any(ListBrokerAccountsQuery.class)))
            .thenReturn(List.of(mockAccount, mockAccount2));

        // Filter by MOCK — should return both
        ToolResponse response = brokerAccountTools.listBrokerAccounts(Optional.of("MOCK"));
        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(2);

        // Filter by ALPACA_PAPER — should return none (no ALPACA_PAPER accounts in mock data)
        ToolResponse responsePaper = brokerAccountTools.listBrokerAccounts(Optional.of("ALPACA_PAPER"));
        assertThat(responsePaper.isError()).isFalse();
        String resultPaper = responsePaper.firstContent().asText().text();
        JsonNode jsonPaper = objectMapper.readTree(resultPaper);
        assertThat(jsonPaper.get("count").asInt()).isEqualTo(0);
    }

    @Test
    void listBrokerAccounts_returnsErrorForInvalidBrokerType() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(listBrokerAccountsUseCase.execute(any(ListBrokerAccountsQuery.class)))
            .thenReturn(List.of());

        ToolResponse response = brokerAccountTools.listBrokerAccounts(Optional.of("INVALID_TYPE"));

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid brokerType");
        assertThat(errorText).contains("MOCK");
        assertThat(errorText).contains("ALPACA_PAPER");
        assertThat(errorText).contains("ALPACA_LIVE");
    }

    private BrokerAccount createTestMockAccount() {
        MockBrokerConfig config = MockBrokerConfig.fromPreset(
            Money.usd(new BigDecimal("100000")),
            com.quorumtrading.model.enums.CostPreset.ZERO_FEES
        );
        return BrokerAccount.createMock(new UserId(USER_ID), "Mock Account", config);
    }
}
