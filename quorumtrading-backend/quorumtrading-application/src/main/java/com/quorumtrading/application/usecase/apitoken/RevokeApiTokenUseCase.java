package com.quorumtrading.application.usecase.apitoken;

import com.quorumtrading.application.usecase.command.RevokeApiTokenCommand;

public interface RevokeApiTokenUseCase {
    void execute(RevokeApiTokenCommand command);
}
