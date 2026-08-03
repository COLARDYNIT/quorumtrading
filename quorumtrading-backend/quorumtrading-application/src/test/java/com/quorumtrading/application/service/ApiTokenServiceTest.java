package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ApiTokenRepository;
import com.quorumtrading.application.usecase.command.CreateApiTokenCommand;
import com.quorumtrading.application.usecase.command.RevokeApiTokenCommand;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;
import com.quorumtrading.application.usecase.result.ResolvedApiToken;
import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiTokenServiceTest {

    private ApiTokenRepository apiTokenRepository;
    private ApiTokenService apiTokenService;

    @BeforeEach
    void setUp() {
        apiTokenRepository = mock(ApiTokenRepository.class);
        apiTokenService = new ApiTokenService(apiTokenRepository);
        when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- Token generation ---

    @Test
    void shouldGenerateTokenWithQtPrefix() {
        String rawToken = apiTokenService.generateRawToken();
        assertThat(rawToken).startsWith("qt_");
    }

    @Test
    void shouldGenerateTokenOfExpectedLength() {
        // qt_ + base64url of 32 bytes = 3 + 43 = 46 chars
        String rawToken = apiTokenService.generateRawToken();
        assertThat(rawToken).hasSizeBetween(45, 47);
    }

    @Test
    void shouldGenerateUniqueTokens() {
        String token1 = apiTokenService.generateRawToken();
        String token2 = apiTokenService.generateRawToken();
        assertThat(token1).isNotEqualTo(token2);
    }

    // --- Token hashing ---

    @Test
    void shouldProduceDeterministicHash() {
        String hash1 = ApiTokenService.hashToken("qt_test123");
        String hash2 = ApiTokenService.hashToken("qt_test123");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldProduceDifferentHashForDifferentTokens() {
        String hash1 = ApiTokenService.hashToken("qt_token_a");
        String hash2 = ApiTokenService.hashToken("qt_token_b");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldProduceSha256HexHash() {
        String hash = ApiTokenService.hashToken("qt_test");
        // SHA-256 hex = 64 lowercase hex chars
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    // --- Create token ---

    @Test
    void shouldCreateTokenAndReturnRawToken() {
        UserId userId = UserId.generate();
        CreateApiTokenCommand command = new CreateApiTokenCommand(userId, "Claude Code", null);

        ApiTokenCreateResult result = apiTokenService.execute(command);

        assertThat(result.rawToken()).startsWith("qt_");
        assertThat(result.name()).isEqualTo("Claude Code");
        assertThat(result.tokenPrefix()).hasSize(8);
        assertThat(result.tokenId()).isNotNull();
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.expiresAt()).isNull();

        verify(apiTokenRepository).save(any(ApiToken.class));
    }

    @Test
    void shouldCreateTokenWithExpiration() {
        UserId userId = UserId.generate();
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        CreateApiTokenCommand command = new CreateApiTokenCommand(userId, "Short-lived", expiresAt);

        ApiTokenCreateResult result = apiTokenService.execute(command);

        assertThat(result.expiresAt()).isNotNull();
    }

    // --- Resolve user by token ---

    @Test
    void shouldResolveUserFromValidToken() {
        UserId userId = UserId.generate();
        String rawToken = "qt_testtoken123";
        String tokenHash = ApiTokenService.hashToken(rawToken);
        ApiTokenId tokenId = ApiTokenId.generate();

        ApiToken token = ApiToken.create(tokenId, userId, "Test", tokenHash, "qt_testt", null);
        when(apiTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        ResolvedApiToken resolved = apiTokenService.execute(rawToken);

        assertThat(resolved.userId()).isEqualTo(userId);
        assertThat(resolved.tokenId()).isEqualTo(tokenId);
    }

    @Test
    void shouldRejectInvalidToken() {
        when(apiTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiTokenService.execute("qt_nonexistent"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid API token");
    }

    @Test
    void shouldRejectRevokedToken() {
        UserId userId = UserId.generate();
        String rawToken = "qt_revokedtoken";
        String tokenHash = ApiTokenService.hashToken(rawToken);
        ApiToken token = ApiToken.create(ApiTokenId.generate(), userId, "Revoked", tokenHash, "qt_revok", null);
        token.revoke();

        when(apiTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> apiTokenService.execute(rawToken))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void shouldRejectExpiredToken() {
        UserId userId = UserId.generate();
        String rawToken = "qt_expiredtoken";
        String tokenHash = ApiTokenService.hashToken(rawToken);
        // Reconstitute with expired date
        ApiToken token = ApiToken.reconstitute(
                ApiTokenId.generate(), userId, "Expired", tokenHash, "qt_expir",
                Instant.now().minus(10, ChronoUnit.DAYS), null,
                Instant.now().minus(1, ChronoUnit.DAYS), false, null);

        when(apiTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> apiTokenService.execute(rawToken))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void shouldRejectNullToken() {
        assertThatThrownBy(() -> apiTokenService.execute((String) null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("required");
    }

    @Test
    void shouldRejectBlankToken() {
        assertThatThrownBy(() -> apiTokenService.execute("  "))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("required");
    }

    // --- List tokens ---

    @Test
    void shouldListTokensForUser() {
        UserId userId = UserId.generate();
        ApiToken token1 = ApiToken.create(ApiTokenId.generate(), userId, "Token 1", "hash1", "qt_tok1x", null);
        ApiToken token2 = ApiToken.create(ApiTokenId.generate(), userId, "Token 2", "hash2", "qt_tok2x", null);

        when(apiTokenRepository.findByUserId(userId)).thenReturn(List.of(token1, token2));

        List<ApiToken> tokens = apiTokenService.execute(userId);

        assertThat(tokens).hasSize(2);
    }

    // --- Revoke token ---

    @Test
    void shouldRevokeOwnedToken() {
        UserId userId = UserId.generate();
        ApiTokenId tokenId = ApiTokenId.generate();
        ApiToken token = ApiToken.create(tokenId, userId, "To Revoke", "hash", "qt_torev", null);

        when(apiTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));

        apiTokenService.execute(new RevokeApiTokenCommand(tokenId, userId));

        verify(apiTokenRepository).save(any(ApiToken.class));
    }

    @Test
    void shouldRejectRevokingOtherUsersToken() {
        UserId ownerUserId = UserId.generate();
        UserId attackerUserId = UserId.generate();
        ApiTokenId tokenId = ApiTokenId.generate();
        ApiToken token = ApiToken.create(tokenId, ownerUserId, "Not Yours", "hash", "qt_notyo", null);

        when(apiTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> apiTokenService.execute(new RevokeApiTokenCommand(tokenId, attackerUserId)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void shouldRejectRevokingNonExistentToken() {
        ApiTokenId tokenId = ApiTokenId.generate();
        UserId userId = UserId.generate();

        when(apiTokenRepository.findById(tokenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiTokenService.execute(new RevokeApiTokenCommand(tokenId, userId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
