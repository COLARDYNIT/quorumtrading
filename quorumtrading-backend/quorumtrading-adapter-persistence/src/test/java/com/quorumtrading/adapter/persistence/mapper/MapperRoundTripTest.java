package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests for the persistence mappers: domain → JPA entity → domain.
 *
 * <p>Mappers were the largest untested surface in the backend — most sat at 0% while carrying every
 * field of every aggregate across the persistence boundary. They are also where a silent failure is
 * most likely and least visible: a field that stops being mapped does not throw, it just comes back
 * null, and the aggregate looks like it was saved that way. Several defects this quarter were
 * exactly that shape.</p>
 *
 * <p>A round trip is the right assertion here rather than field-by-field checks on one direction.
 * It pins the property that actually matters — that what goes in comes back — and it fails when
 * either direction drops a field, which one-way tests do not.</p>
 */
@QuarkusTest
@DisplayName("Persistence mappers — round trip")
class MapperRoundTripTest {

    @Inject UserMapper userMapper;
    @Inject StrategyMapper strategyMapper;
    @Inject ChallengeMapper challengeMapper;
    @Inject NotificationMapper notificationMapper;
    @Inject InvestmentPlanMapper investmentPlanMapper;
    @Inject InvestmentAllocationMapper investmentAllocationMapper;

    private static final UserId USER_ID = UserId.generate();

    @Nested
    @DisplayName("User")
    class UserRoundTrip {

        private User user() {
            return User.create(USER_ID, "trader@example.com", "Trader", "https://img/a.png",
                    OAuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("identity and profile survive the round trip")
        void shouldRoundTrip() {
            User back = userMapper.toDomain(userMapper.toJpaEntity(user()));

            assertThat(back.getId()).isEqualTo(USER_ID);
            assertThat(back.getEmail()).isEqualTo("trader@example.com");
            assertThat(back.getDisplayName()).isEqualTo("Trader");
            assertThat(back.getAvatarUrl()).isEqualTo("https://img/a.png");
            assertThat(back.getOauthProviders())
                    .extracting(com.quorumtrading.model.entity.UserOAuthProvider::getProvider)
                    .contains(OAuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("an absent avatar stays absent rather than becoming a blank string")
        void shouldPreserveANullAvatar() {
            User withoutAvatar = User.create(USER_ID, "a@b.c", "A", null, OAuthProvider.GITLAB);

            User back = userMapper.toDomain(userMapper.toJpaEntity(withoutAvatar));

            assertThat(back.getAvatarUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("Strategy")
    class StrategyRoundTrip {

        @Test
        @DisplayName("content and ownership survive the round trip")
        void shouldRoundTrip() {
            StrategyId id = StrategyId.generate();
            Strategy strategy = new Strategy(id, "BTC Momentum", "---\ncrypto_tickers:\n  - BTC\n---\nBuy.",
                    USER_ID);

            Strategy back = strategyMapper.toDomain(strategyMapper.toJpaEntity(strategy));

            assertThat(back.getId()).isEqualTo(id);
            assertThat(back.getName()).isEqualTo("BTC Momentum");
            assertThat(back.getContent()).contains("crypto_tickers");
            assertThat(back.getOwnerId()).isEqualTo(USER_ID);
            assertThat(back.getStatus()).isEqualTo(strategy.getStatus());
            assertThat(back.isEnabled()).isEqualTo(strategy.isEnabled());
        }

        @Test
        @DisplayName("the markdown body is carried verbatim — it is the strategy")
        void shouldNotAlterTheMarkdown() {
            String content = "---\nversion: v1.0\nconsensus_method: MAJORITY\n"
                    + "crypto_tickers:\n  - BTC\n---\n\n# Title\n\nRule one.\n";
            Strategy strategy = new Strategy(StrategyId.generate(), "S", content, USER_ID);

            Strategy back = strategyMapper.toDomain(strategyMapper.toJpaEntity(strategy));

            assertThat(back.getContent()).isEqualTo(content);
        }
    }

    @Nested
    @DisplayName("Challenge")
    class ChallengeRoundTrip {

        @Test
        @DisplayName("ticker, cash and the four dates survive the round trip")
        void shouldRoundTrip() {
            ChallengeId id = new ChallengeId(UUID.randomUUID());
            Challenge challenge = Challenge.reconstitute(id, "june-btc", "June BTC", "A contest",
                    new Symbol("BTC", InvestmentType.CRYPTO), Money.usd(new BigDecimal("10000")),
                    Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-30T00:00:00Z"),
                    Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-05-31T00:00:00Z"),
                    Instant.parse("2026-04-01T00:00:00Z"), USER_ID, true);

            Challenge back = challengeMapper.toDomain(challengeMapper.toJpaEntity(challenge));

            assertThat(back.getChallengeId()).isEqualTo(id);
            assertThat(back.getSlug()).isEqualTo("june-btc");
            assertThat(back.getTicker().ticker()).isEqualTo("BTC");
            assertThat(back.getInitialCash().amount()).isEqualByComparingTo("10000");
            assertThat(back.getSimulationStartDate()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
            assertThat(back.getParticipationClose()).isEqualTo(Instant.parse("2026-05-31T00:00:00Z"));
            assertThat(back.isStrategiesPublic()).isTrue();
        }
    }

    @Nested
    @DisplayName("Notification")
    class NotificationRoundTrip {

        @Test
        @DisplayName("type, channel and delivery state survive the round trip")
        void shouldRoundTrip() {
            NotificationId id = NotificationId.generate();
            UUID related = UUID.randomUUID();
            Notification n = Notification.reconstitute(id, USER_ID,
                    NotificationType.INVESTMENT_EXECUTED, NotificationChannel.EMAIL,
                    "Invested", "$250 into BTC", Map.of("amount", "250.00"), related,
                    NotificationStatus.DELIVERED, Instant.parse("2026-06-01T06:00:00Z"),
                    Instant.parse("2026-06-01T06:00:01Z"), null, null);

            Notification back = notificationMapper.toDomain(notificationMapper.toJpaEntity(n));

            assertThat(back.getId()).isEqualTo(id);
            assertThat(back.getType()).isEqualTo(NotificationType.INVESTMENT_EXECUTED);
            assertThat(back.getChannel())
                    .as("the channel decides whether this was an email or an inbox row")
                    .isEqualTo(NotificationChannel.EMAIL);
            assertThat(back.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
            assertThat(back.getTitle()).isEqualTo("Invested");
            assertThat(back.getRelatedEntityId()).isEqualTo(related);
        }

        @Test
        @DisplayName("a failed notification keeps its reason")
        void shouldCarryAFailureReason() {
            Notification failed = Notification.reconstitute(NotificationId.generate(), USER_ID,
                    NotificationType.INVESTMENT_FAILED, NotificationChannel.EMAIL, "Failed", "body",
                    Map.of(), null, NotificationStatus.FAILED,
                    Instant.parse("2026-06-01T06:00:00Z"), null, null, "SMTP timeout");

            Notification back = notificationMapper.toDomain(notificationMapper.toJpaEntity(failed));

            assertThat(back.getFailureReason()).isEqualTo("SMTP timeout");
        }
    }

    @Nested
    @DisplayName("InvestmentPlan")
    class InvestmentPlanRoundTrip {

        @Test
        @DisplayName("budget, schedule and picker preferences survive the round trip")
        void shouldRoundTrip() {
            InvestmentPlanId id = InvestmentPlanId.generate();
            InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                    "conservative", List.of("AAPL", "MSFT"), List.of("GME"), 2, false);
            InvestmentPlan plan = InvestmentPlan.create(id, USER_ID,
                    com.quorumtrading.model.valueobject.BrokerAccountId.generate(),
                    BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("250.00")), 15,
                    PickerPolicy.AI_PICK, prefs, Instant.parse("2026-05-15T00:00:00Z"));

            InvestmentPlan back = investmentPlanMapper.toDomain(
                    investmentPlanMapper.toJpaEntity(plan));

            assertThat(back.getId()).isEqualTo(id);
            assertThat(back.getBudgetMode()).isEqualTo(BudgetMode.FIXED_AMOUNT);
            assertThat(back.getAmount().amount()).isEqualByComparingTo("250.00");
            assertThat(back.getDayOfMonth()).isEqualTo(15);
            assertThat(back.getStatus()).isEqualTo(plan.getStatus());

            InvestmentPlanPreferences backPrefs = back.getPreferences();
            assertThat(backPrefs.riskTolerance()).isEqualTo("conservative");
            assertThat(backPrefs.allowedTickers()).containsExactly("AAPL", "MSFT");
            assertThat(backPrefs.blockedTickers()).containsExactly("GME");
            assertThat(backPrefs.maxStrategiesActive())
                    .as("a cap that does not survive persistence is a cap that does not exist")
                    .isEqualTo(2);
            assertThat(backPrefs.preferExistingHoldings()).isFalse();
        }
    }

    @Nested
    @DisplayName("InvestmentAllocation")
    class InvestmentAllocationRoundTrip {

        @Test
        @DisplayName("an executed allocation keeps the trail back to its simulation")
        void shouldRoundTripAnExecutedRow() {
            InvestmentAllocationId id = InvestmentAllocationId.generate();
            InvestmentPlanId planId = InvestmentPlanId.generate();
            SimulationId simId = new SimulationId(UUID.randomUUID());
            StrategyId strategyId = StrategyId.generate();

            InvestmentAllocation allocation = InvestmentAllocation.create(id, planId, USER_ID,
                    Instant.parse("2026-06-01T06:00:00Z"), AllocationStatus.EXECUTED,
                    Money.usd(new BigDecimal("250.00")), Money.usd(new BigDecimal("250.00")),
                    strategyId, "picked the most recently updated strategy", BigDecimal.ZERO,
                    AllocationAction.NEW_SIMULATION, simId, null, List.of(), null,
                    Instant.parse("2026-06-01T06:00:05Z"));

            InvestmentAllocation back = investmentAllocationMapper.toDomain(
                    investmentAllocationMapper.toJpaEntity(allocation));

            assertThat(back.getId()).isEqualTo(id);
            assertThat(back.getStatus()).isEqualTo(AllocationStatus.EXECUTED);
            assertThat(back.getTargetSimulationId()).isEqualTo(simId);
            assertThat(back.getChosenStrategyId()).isEqualTo(strategyId);
            assertThat(back.getActualAmount().amount()).isEqualByComparingTo("250.00");
            assertThat(back.getPickerReasoning()).contains("most recently updated");
        }

        @Test
        @DisplayName("a skipped allocation keeps its reason — the row is the only trace")
        void shouldRoundTripASkippedRow() {
            InvestmentAllocation skipped = InvestmentAllocation.create(
                    InvestmentAllocationId.generate(), InvestmentPlanId.generate(), USER_ID,
                    Instant.parse("2026-06-01T06:00:00Z"),
                    AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS,
                    Money.usd(new BigDecimal("250.00")), null, null, null, null, null, null, null,
                    List.of(), "Broker available balance $12.00 is less than the plan amount $250.00",
                    Instant.parse("2026-06-01T06:00:05Z"));

            InvestmentAllocation back = investmentAllocationMapper.toDomain(
                    investmentAllocationMapper.toJpaEntity(skipped));

            assertThat(back.getStatus()).isEqualTo(AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS);
            assertThat(back.getFailureReason()).contains("less than the plan amount");
            assertThat(back.getActualAmount()).isNull();
        }
    }
}
