package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;

public record RevokeApiTokenCommand(ApiTokenId tokenId, UserId userId) {
}
