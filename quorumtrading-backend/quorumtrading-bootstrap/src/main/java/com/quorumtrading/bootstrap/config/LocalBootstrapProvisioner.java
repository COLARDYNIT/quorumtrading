package com.quorumtrading.bootstrap.config;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.AuthenticateUserUseCase;
import com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase;
import com.quorumtrading.application.usecase.command.AuthenticateUserCommand;
import com.quorumtrading.application.usecase.command.CreateApiTokenCommand;
import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Self-host (local edition) bootstrap: on startup, ensure the single local owner exists and a
 * personal MCP API token is provisioned, then print a copy-paste banner so an agentic user can
 * wire Claude Code to the MCP endpoint without any manual sign-in/token dance.
 *
 * <p><b>Disabled by default</b> (gated on {@code quorumtrading.local.auto-provision.enabled});
 * the Docker {@code docker-compose.local.yml} sets it on. It must never run in production.</p>
 *
 * <p>The provisioned identity is intentionally the <em>same</em> user the {@code /auth/dev-login}
 * endpoint creates (same email + provider + sub) so that browser cookie-auth (used by the Saxo
 * onboarding {@code /start} endpoint) and the MCP bearer token resolve to one and the same user —
 * otherwise a broker account linked in the browser would be invisible to the agent.</p>
 *
 * <p>The raw token is written once to a file on a persistent volume; on every boot the token is
 * re-read from that file and re-printed, so it is always retrievable from {@code docker logs} and
 * provisioning stays idempotent across restarts. Any failure is logged and swallowed — provisioning
 * never blocks startup.</p>
 */
@ApplicationScoped
public class LocalBootstrapProvisioner {

    private static final Logger LOG = Logger.getLogger(LocalBootstrapProvisioner.class);

    @ConfigProperty(name = "quorumtrading.local.auto-provision.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "quorumtrading.local.auto-provision.token-file",
            defaultValue = "/work/.qt-local/mcp-token")
    String tokenFilePath;

    @ConfigProperty(name = "quorumtrading.local.auto-provision.user-email",
            defaultValue = "local@quorumtrading.local")
    String userEmail;

    @ConfigProperty(name = "quorumtrading.local.auto-provision.user-name",
            defaultValue = "Local")
    String userName;

    @ConfigProperty(name = "quorumtrading.local.auto-provision.provider-sub",
            defaultValue = "dev-login-local-sub")
    String providerSub;

    @ConfigProperty(name = "quorumtrading.local.auto-provision.mcp-url",
            defaultValue = "http://localhost:8085/mcp")
    String mcpUrl;

    @ConfigProperty(name = "quorumtrading.llm.selection.enabled", defaultValue = "false")
    boolean llmSelectionEnabled;

    /**
     * When no engine is chosen yet, auto-select the first available one at boot (free CLI, then a
     * free local server, then OpenRouter) so a self-hoster with a local LLM running gets a working
     * stack with zero clicks. Never overrides an explicit selection. On in {@code %local}.
     */
    @ConfigProperty(name = "quorumtrading.llm.auto-select.enabled", defaultValue = "false")
    boolean llmAutoSelectEnabled;

    @ConfigProperty(name = "quorumtrading.llm.setup-url",
            defaultValue = "http://localhost:8085/api/v1/llm/setup")
    String llmSetupUrl;

    @Inject
    AuthenticateUserUseCase authenticateUserUseCase;

    @Inject
    UserRepository userRepository;

    @Inject
    CreateApiTokenUseCase createApiTokenUseCase;

    @Inject
    GetLlmProviderStatusUseCase getLlmProviderStatusUseCase;

    @Inject
    SelectLlmProviderUseCase selectLlmProviderUseCase;

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            return;
        }
        try {
            UserId userId = ensureOwner();
            String rawToken = ensureToken(userId);
            printBanner(rawToken);
        } catch (Exception e) {
            // Never let provisioning crash the app — log loudly and carry on.
            LOG.errorf(e, "Local auto-provision failed: %s. The app is still up; you can create an "
                    + "API token manually via /auth/dev-login + /settings/api-tokens.", e.getMessage());
        }
        autoSelectLlmIfNeeded();
        printLlmNudge();
    }

    /**
     * Zero-friction engine selection: if nothing is chosen yet, pick the first available provider so
     * a self-hoster who already has a local LLM running gets a working stack without a single click.
     *
     * <p>Priority is free-first: {@link LlmProvider#LOCAL} (a host CLI), then {@link
     * LlmProvider#LOCAL_HTTP} (a local OpenAI-compatible server such as Ollama, reachable in Docker),
     * then {@link LlmProvider#OPENROUTER} (only if a key is already configured). An explicit user
     * selection is never overridden, and if nothing is available boot proceeds unselected — the
     * nudge below then explains what to do.</p>
     */
    private void autoSelectLlmIfNeeded() {
        if (!llmSelectionEnabled || !llmAutoSelectEnabled) {
            return;
        }
        try {
            LlmProviderStatus status = getLlmProviderStatusUseCase.get();
            if (status.isSelected()) {
                return;
            }
            for (LlmProvider candidate : new LlmProvider[]{
                    LlmProvider.LOCAL, LlmProvider.LOCAL_HTTP, LlmProvider.OPENROUTER}) {
                if (!isAvailable(status, candidate)) {
                    continue;
                }
                try {
                    selectLlmProviderUseCase.select(new SelectLlmProviderUseCase.Command(candidate, null));
                    LOG.infof("Auto-selected LLM engine: %s (first available). Change anytime at %s",
                            candidate, llmSetupUrl);
                    return;
                } catch (Exception selectFailure) {
                    LOG.warnf("Auto-select of %s failed (%s); trying the next available engine",
                            candidate, selectFailure.getMessage());
                }
            }
        } catch (Exception e) {
            // Auto-select is a convenience; never block or fail boot over it.
            LOG.warnf("LLM auto-select skipped: %s", e.getMessage());
        }
    }

    /**
     * One LLM-engine status line under the MCP banner — the banner is where a human looks first.
     * By the time this runs, {@link #autoSelectLlmIfNeeded()} has already tried to pick an engine;
     * this reports the outcome (active engine, or what to do if none is available).
     */
    private void printLlmNudge() {
        if (!llmSelectionEnabled) {
            return;
        }
        try {
            LlmProviderStatus status = getLlmProviderStatusUseCase.get();
            if (status.isSelected()) {
                LOG.infof("LLM engine: %s (active) — change anytime at %s", status.selected(), llmSetupUrl);
                return;
            }
            boolean localAvailable = isAvailable(status, LlmProvider.LOCAL);
            boolean localHttpAvailable = isAvailable(status, LlmProvider.LOCAL_HTTP);
            boolean openRouterAvailable = isAvailable(status, LlmProvider.OPENROUTER);
            LOG.warnf("\n  !! No LLM engine selected yet — AI features wait until you choose."
                    + "\n     local CLI: %s | local server: %s | openrouter: %s"
                    + "\n     Free, keyless options — pick whichever you already have:"
                    + "\n       * a local model server: `ollama serve` (or LM Studio / llama.cpp / vLLM)"
                    + "\n       * an LLM CLI you already use (`claude`, `codex`, `gemini`, `qwen`) — under"
                    + "\n         Docker the container cannot invoke it, so run the host bridge instead:"
                    + "\n           python3 scripts/local-llm-cli-shim.py"
                    + "\n           QT_LLM_LOCALHTTP_URL=http://host.docker.internal:11435/v1 \\"
                    + "\n             QT_LLM_LOCALHTTP_MODEL=claude \\"
                    + "\n             docker compose -f docker-compose.local.yml up -d backend"
                    + "\n     Or choose here: %s"
                    + "\n     (or ask your agent to call the choose_llm_provider MCP tool)",
                    localAvailable ? "available" : "not found",
                    localHttpAvailable ? "reachable" : "not found",
                    openRouterAvailable ? "key configured" : "no key",
                    llmSetupUrl);
        } catch (Exception e) {
            // Status is a nicety; never block or fail boot over it.
            LOG.warnf("Could not determine LLM engine status: %s", e.getMessage());
        }
    }

    private static boolean isAvailable(LlmProviderStatus status, LlmProvider provider) {
        return status.providers().stream()
                .anyMatch(p -> p.provider() == provider && p.available());
    }

    private UserId ensureOwner() {
        User user = authenticateUserUseCase.execute(new AuthenticateUserCommand(
                userEmail, userName, null, OAuthProvider.GOOGLE, providerSub));

        // The owner of a self-hosted box administers it. Without this they are created as a plain
        // USER and locked out of every admin screen on their own machine — feature flags in
        // particular, which is the one place they are meant to switch things on for themselves.
        //
        // Runs on every boot, not just first-run: installs provisioned before this existed already
        // have a USER row, and a failed promotion self-heals on the next restart. Promotion is
        // scoped to this provisioner, which only runs on the self-host edition
        // (quorumtrading.local.auto-provision.enabled), so it cannot grant admin on the hosted
        // product.
        if (!user.isAdmin()) {
            user.promoteToAdmin();
            userRepository.save(user);
            LOG.infof("Promoted local owner %s to ADMIN — a self-hoster administers their own instance",
                    userEmail);
        }

        LOG.infof("Local owner ready: %s (%s)", userEmail, user.getId().value());
        return user.getId();
    }

    /** Returns the raw MCP token, minting + persisting it on first boot, re-reading it thereafter. */
    private String ensureToken(UserId userId) throws IOException {
        Path tokenFile = Path.of(tokenFilePath);
        if (Files.exists(tokenFile) && Files.size(tokenFile) > 0) {
            return Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        }

        ApiTokenCreateResult result = createApiTokenUseCase.execute(
                new CreateApiTokenCommand(userId, "Local MCP (auto-provisioned)", null));
        String rawToken = result.rawToken();

        if (tokenFile.getParent() != null) {
            Files.createDirectories(tokenFile.getParent());
        }
        Files.writeString(tokenFile, rawToken, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem — best effort only.
        }
        LOG.infof("Minted and persisted local MCP token to %s", tokenFilePath);
        return rawToken;
    }

    private void printBanner(String rawToken) {
        String line = "=".repeat(70);
        LOG.info("\n" + line
                + "\n  QuorumTrading (local edition) — MCP ready"
                + "\n" + line
                + "\n  MCP endpoint : " + mcpUrl
                + "\n  Token        : " + rawToken
                + "\n"
                + "\n  Wire Claude Code in one command:"
                + "\n    claude mcp add --transport http quorumtrading " + mcpUrl
                + " \\"
                + "\n      --header \"Authorization: Bearer " + rawToken + "\""
                + "\n"
                + "\n  Or paste into .mcp.json (see .mcp.json.example)."
                + "\n" + line);
    }
}
