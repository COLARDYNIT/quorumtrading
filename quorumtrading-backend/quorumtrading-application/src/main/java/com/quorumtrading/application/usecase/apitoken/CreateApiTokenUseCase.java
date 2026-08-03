package com.quorumtrading.application.usecase.apitoken;

import com.quorumtrading.application.usecase.command.CreateApiTokenCommand;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;

public interface CreateApiTokenUseCase {
    ApiTokenCreateResult execute(CreateApiTokenCommand command);
}
