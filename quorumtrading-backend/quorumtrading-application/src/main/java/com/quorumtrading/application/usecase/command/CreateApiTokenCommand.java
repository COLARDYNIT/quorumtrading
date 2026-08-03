package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;

public record CreateApiTokenCommand(UserId userId, String name, Instant expiresAt) {
}
