package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.AuthenticateUserCommand;
import com.quorumtrading.model.entity.User;

public interface AuthenticateUserUseCase {
    User execute(AuthenticateUserCommand command);
}
