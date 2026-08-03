package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link BrokerAccountRepository#findByIds} — the batch
 * lookup that backs the GET /simulations list mapping (single IN-query instead
 * of one findById per simulation).
 */
@QuarkusTest
class BrokerAccountRepositoryFindByIdsIntegrationTest {

    @Inject
    BrokerAccountRepository repository;

    @Inject
    UserRepository userRepository;

    private UserId createUser() {
        UserId userId = UserId.generate();
        User user = User.create(
            userId,
            "find-by-ids-" + UUID.randomUUID() + "@example.com",
            "FindByIds Test",
            null,
            OAuthProvider.GOOGLE);
        userRepository.save(user);
        return userId;
    }

    private BrokerAccount createAccount(UserId userId, String name) {
        BrokerCredentials creds = new ApiKeyCredentials("PKTEST-" + name, "secret-" + name);
        Money balance = Money.usd(new BigDecimal("10000.00"));
        return repository.save(BrokerAccount.createReal(
            userId, name, BrokerType.ALPACA_PAPER, creds, balance, balance));
    }

    @Test
    @DisplayName("shouldBatchFindBrokerAccountsByIds_WhenIdsExist")
    void shouldBatchFindBrokerAccountsByIds_WhenIdsExist() {
        // Arrange
        UserId userId = createUser();
        BrokerAccount account1 = createAccount(userId, "Batch A");
        BrokerAccount account2 = createAccount(userId, "Batch B");
        BrokerAccount account3 = createAccount(userId, "Batch C");
        BrokerAccountId unknownId = BrokerAccountId.generate();

        // Act: request two of the three saved accounts plus an unknown id
        List<BrokerAccount> found = repository.findByIds(
            List.of(account1.getId(), account3.getId(), unknownId));

        // Assert: exactly the requested-and-existing rows come back
        assertThat(found).extracting(BrokerAccount::getId)
            .containsExactlyInAnyOrder(account1.getId(), account3.getId());
        assertThat(found).extracting(BrokerAccount::getId)
            .doesNotContain(account2.getId(), unknownId);
    }

    @Test
    @DisplayName("shouldReturnEmptyList_WhenFindByIdsGivenEmptyOrNull")
    void shouldReturnEmptyList_WhenFindByIdsGivenEmptyOrNull() {
        assertThat(repository.findByIds(List.of())).isEmpty();
        assertThat(repository.findByIds(null)).isEmpty();
    }
}
