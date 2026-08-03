package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Output port for API token persistence.
 */
public interface ApiTokenRepository {

    ApiToken save(ApiToken token);

    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findByUserId(UserId userId);

    Optional<ApiToken> findById(ApiTokenId id);

    void deleteById(ApiTokenId id);
}
