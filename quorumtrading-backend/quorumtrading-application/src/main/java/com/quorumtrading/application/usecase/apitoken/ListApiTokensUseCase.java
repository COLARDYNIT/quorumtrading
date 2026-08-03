package com.quorumtrading.application.usecase.apitoken;

import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

public interface ListApiTokensUseCase {
    List<ApiToken> execute(UserId userId);
}
