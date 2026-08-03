package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A plan's broker account decides where a scheduled contribution spends from, so choosing one on
 * update is exactly as consequential as choosing one at creation. The update path used to accept any
 * UUID with no existence, ownership or live-money check, which let a plan created against a mock
 * account be repointed at a live one and start trading real money with no acknowledgement.
 */
class InvestmentPlanBrokerGuardTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId STRANGER = new UserId(UUID.randomUUID());

    private InvestmentPlanRepository planRepository;
    private BrokerAccountRepository brokerAccountRepository;
    private InvestmentPlanService service;
    private InvestmentPlanId planId;

    @BeforeEach
    void setUp() {
        planRepository = mock(InvestmentPlanRepository.class);
        brokerAccountRepository = mock(BrokerAccountRepository.class);
        service = new InvestmentPlanService(
            planRepository, mock(InvestmentAllocationRepository.class), brokerAccountRepository);

        planId = InvestmentPlanId.generate();
        InvestmentPlan plan = mock(InvestmentPlan.class);
        when(plan.getUserId()).thenReturn(OWNER);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(InvestmentPlan.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BrokerAccountId brokerOfType(BrokerType type, UserId owner) {
        BrokerAccountId id = new BrokerAccountId(UUID.randomUUID());
        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getUserId()).thenReturn(owner);
        when(account.getBrokerType()).thenReturn(type);
        when(brokerAccountRepository.findById(id)).thenReturn(Optional.of(account));
        return id;
    }

    private UpdateInvestmentPlanCommand repointTo(BrokerAccountId broker, boolean confirmLive) {
        return new UpdateInvestmentPlanCommand(
            planId, OWNER, Optional.of(broker), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), confirmLive);
    }

    @Nested
    @DisplayName("Repointing a plan at a live broker requires the same consent as creating one")
    class LiveConsentOnUpdate {

        @Test
        @DisplayName("moving onto an ALPACA_LIVE account without confirmLive is refused")
        void liveWithoutConsentIsRefused() {
            BrokerAccountId live = brokerOfType(BrokerType.ALPACA_LIVE, OWNER);

            assertThatThrownBy(() -> service.execute(repointTo(live, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmLive");

            verify(planRepository, never()).save(any());
        }

        @Test
        @DisplayName("moving onto a SAXO_LIVE account without confirmLive is refused too")
        void everyLiveVenueIsCovered() {
            // The old guard compared against ALPACA_LIVE only, so other live venues slipped past.
            BrokerAccountId live = brokerOfType(BrokerType.SAXO_LIVE, OWNER);

            assertThatThrownBy(() -> service.execute(repointTo(live, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmLive");
        }

        @Test
        @DisplayName("with explicit consent the move is allowed")
        void liveWithConsentIsAllowed() {
            BrokerAccountId live = brokerOfType(BrokerType.ALPACA_LIVE, OWNER);

            assertThatCode(() -> service.execute(repointTo(live, true))).doesNotThrowAnyException();
            verify(planRepository).save(any(InvestmentPlan.class));
        }

        @Test
        @DisplayName("a non-live account needs no acknowledgement")
        void paperNeedsNoConsent() {
            BrokerAccountId paper = brokerOfType(BrokerType.ALPACA_PAPER, OWNER);

            assertThatCode(() -> service.execute(repointTo(paper, false))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("The account itself is validated on update")
    class AccountValidationOnUpdate {

        @Test
        @DisplayName("an unknown broker account id is rejected, not silently stored")
        void unknownAccountIsRejected() {
            BrokerAccountId ghost = new BrokerAccountId(UUID.randomUUID());
            when(brokerAccountRepository.findById(ghost)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.execute(repointTo(ghost, false)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Broker account not found");

            verify(planRepository, never()).save(any());
        }

        @Test
        @DisplayName("someone else's broker account is rejected")
        void foreignAccountIsRejected() {
            BrokerAccountId foreign = brokerOfType(BrokerType.ALPACA_PAPER, STRANGER);

            assertThatThrownBy(() -> service.execute(repointTo(foreign, false)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong to user");

            verify(planRepository, never()).save(any());
        }

        @Test
        @DisplayName("an update that does not touch the broker does not consult the repository")
        void unrelatedUpdateSkipsBrokerValidation() {
            UpdateInvestmentPlanCommand dayOnly = new UpdateInvestmentPlanCommand(
                planId, OWNER, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(14), Optional.empty(), false);

            assertThatCode(() -> service.execute(dayOnly)).doesNotThrowAnyException();
            verify(brokerAccountRepository, never()).findById(any());
        }
    }
}
