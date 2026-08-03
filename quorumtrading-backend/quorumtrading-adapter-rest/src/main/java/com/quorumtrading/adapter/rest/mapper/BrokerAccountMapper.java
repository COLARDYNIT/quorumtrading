package com.quorumtrading.adapter.rest.mapper;

import com.quorumtrading.adapter.rest.dto.response.BrokerAccountResponse;
import com.quorumtrading.adapter.rest.dto.response.BrokerAccountResponse.MockBrokerConfigResponse;
import com.quorumtrading.adapter.rest.dto.response.BrokerAccountResponse.MoneyDto;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between BrokerAccount domain objects and REST DTOs.
 */
@ApplicationScoped
public class BrokerAccountMapper {

    /**
     * Convert domain BrokerAccount to response DTO.
     *
     * @param brokerAccount the domain broker account object
     * @return the response DTO
     */
    public BrokerAccountResponse toResponse(BrokerAccount brokerAccount) {
        return new BrokerAccountResponse(
            brokerAccount.getId().value().toString(),
            brokerAccount.getUserId().value().toString(),
            brokerAccount.getName(),
            brokerAccount.getBrokerType().name(),
            toMoneyDto(brokerAccount.getTotalBalance()),
            brokerAccount.getEquity() != null ? toMoneyDto(brokerAccount.getEquity()) : null,
            toMoneyDto(brokerAccount.getReservedBalance()),
            toMoneyDto(brokerAccount.getAvailableBalance()),
            brokerAccount.getStatus().name(),
            toMockConfigResponse(brokerAccount.getMockConfig()),
            brokerAccount.getCreatedAt(),
            brokerAccount.getUpdatedAt()
        );
    }

    /**
     * Convert list of domain BrokerAccounts to list of response DTOs.
     *
     * @param brokerAccounts the list of domain broker accounts
     * @return the list of response DTOs
     */
    public List<BrokerAccountResponse> toResponseList(List<BrokerAccount> brokerAccounts) {
        return brokerAccounts.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private MoneyDto toMoneyDto(Money money) {
        return new MoneyDto(
            money.amount(),
            money.currency().name()
        );
    }

    private MockBrokerConfigResponse toMockConfigResponse(MockBrokerConfig config) {
        if (config == null) {
            return null;
        }
        return new MockBrokerConfigResponse(
            config.initialCapital().amount(),
            config.preset().name(),
            config.commissionRate(),
            config.cryptoCommissionRate(),
            config.slippageBase(),
            config.slippageImpactPerUnit(),
            config.slippageVolatilityMultiplier()
        );
    }
}
