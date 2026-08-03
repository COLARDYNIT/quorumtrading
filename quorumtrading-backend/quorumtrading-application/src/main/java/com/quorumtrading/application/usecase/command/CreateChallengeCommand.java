package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;

/**
 * Command for creating a challenge.
 *
 * @param name display name
 * @param description optional markdown description
 * @param ticker single ticker for the challenge
 * @param initialCash fixed initial cash for all participants
 * @param simulationStartDate when the trading simulation begins (can be historical)
 * @param simulationEndDate when the trading simulation ends
 * @param participationOpen when users can start joining
 * @param participationClose when joining closes
 * @param requestingUserId admin user creating the challenge
 */
public record CreateChallengeCommand(
    String name,
    String description,
    Symbol ticker,
    Money initialCash,
    Instant simulationStartDate,
    Instant simulationEndDate,
    Instant participationOpen,
    Instant participationClose,
    UserId requestingUserId,
    boolean strategiesPublic
) {
}
