package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ApiTokenRepository;
import com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase;
import com.quorumtrading.application.usecase.apitoken.ListApiTokensUseCase;
import com.quorumtrading.application.usecase.apitoken.ResolveUserByApiTokenUseCase;
import com.quorumtrading.application.usecase.apitoken.RevokeApiTokenUseCase;
import com.quorumtrading.application.usecase.command.CreateApiTokenCommand;
import com.quorumtrading.application.usecase.command.RevokeApiTokenCommand;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;
import com.quorumtrading.application.usecase.result.ResolvedApiToken;
import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Application service implementing API token CRUD and resolution.
 * Pure Java - no framework annotations.
 */
public class ApiTokenService implements CreateApiTokenUseCase, ListApiTokensUseCase,
        RevokeApiTokenUseCase, ResolveUserByApiTokenUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ApiTokenService.class);
    private static final String TOKEN_PREFIX = "qt_";
    private static final int TOKEN_RANDOM_BYTES = 32;
    private static final int DISPLAY_PREFIX_LENGTH = 8;

    private final ApiTokenRepository apiTokenRepository;
    private final SecureRandom secureRandom;

    public ApiTokenService(ApiTokenRepository apiTokenRepository) {
        this.apiTokenRepository = apiTokenRepository;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public ApiTokenCreateResult execute(CreateApiTokenCommand command) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        String tokenPrefix = rawToken.substring(0, DISPLAY_PREFIX_LENGTH);

        ApiTokenId tokenId = ApiTokenId.generate();
        ApiToken token = ApiToken.create(
                tokenId,
                command.userId(),
                command.name(),
                tokenHash,
                tokenPrefix,
                command.expiresAt()
        );

        apiTokenRepository.save(token);
        logger.info("Created API token '{}' (prefix={}) for user {}", command.name(), tokenPrefix, command.userId());

        return new ApiTokenCreateResult(
                tokenId,
                rawToken,
                token.getName(),
                tokenPrefix,
                token.getCreatedAt(),
                token.getExpiresAt()
        );
    }

    @Override
    public List<ApiToken> execute(UserId userId) {
        return apiTokenRepository.findByUserId(userId);
    }

    @Override
    public void execute(RevokeApiTokenCommand command) {
        ApiToken token = apiTokenRepository.findById(command.tokenId())
                .orElseThrow(() -> new IllegalArgumentException("Token not found: " + command.tokenId()));

        if (!token.getUserId().equals(command.userId())) {
            throw new SecurityException("Token does not belong to user: " + command.userId());
        }

        token.revoke();
        apiTokenRepository.save(token);
        logger.info("Revoked API token '{}' (prefix={}) for user {}", token.getName(), token.getTokenPrefix(), command.userId());
    }

    @Override
    public ResolvedApiToken execute(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new SecurityException("API token is required");
        }

        String tokenHash = hashToken(rawToken);
        ApiToken token = apiTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new SecurityException("Invalid API token"));

        if (!token.isActive()) {
            if (token.isRevoked()) {
                throw new SecurityException("API token has been revoked");
            }
            if (token.isExpired()) {
                throw new SecurityException("API token has expired");
            }
            throw new SecurityException("API token is not active");
        }

        return new ResolvedApiToken(token.getUserId(), token.getId());
    }

    /**
     * Generates a raw token: "qt_" + 32 random bytes encoded as base64url (no padding).
     */
    String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return TOKEN_PREFIX + encoded;
    }

    /**
     * Computes SHA-256 hash of the full token string (including prefix).
     * Returns lowercase hex string.
     */
    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
