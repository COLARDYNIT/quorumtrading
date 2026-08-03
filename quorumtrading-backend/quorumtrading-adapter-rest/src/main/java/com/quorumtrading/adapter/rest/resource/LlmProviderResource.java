package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.LlmProviderSelectRequest;
import com.quorumtrading.adapter.rest.dto.response.LlmProviderStatusResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.model.enums.LlmProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST edge for runtime LLM provider selection (self-host edition).
 *
 * <p>Lets the local user choose between the local CLI engine and OpenRouter <em>after</em> boot —
 * no env vars, no restart. JSON endpoints serve agents/scripts (and the web app's first-run
 * takeover); {@code /setup} renders a server-side HTML page for humans following the boot-banner
 * link.</p>
 *
 * <p><b>Design:</b> the HTML implements the claude.ai/design handoff "LLM Provider Setup.html" —
 * one {@code <main class="setup-card state-X">} per render, where {@code state-choose} /
 * {@code state-confirmed} / {@code state-none} pick the state and each provider tile carries
 * {@code opt-available} / {@code opt-unavailable} / {@code opt-configured} / {@code opt-needskey}
 * / {@code opt-active} availability classes. Forms post (form-encoded) to
 * {@code /api/v1/llm/providers/select} with lowercase provider values; no JavaScript.</p>
 *
 * <p><b>Gating &amp; auth:</b> every endpoint 404s unless {@code quorumtrading.llm.selection.enabled}
 * is on. The endpoints are deliberately anonymous: the flag is only ever enabled in the single-user
 * self-host edition, where the anonymous {@code /auth/dev-login} endpoint already grants full
 * account access to anyone who can reach localhost — a session requirement here would add friction
 * (the browser has no cookie when opening the banner link) without adding security. The hosted
 * SaaS keeps the flag off and these endpoints do not exist there.</p>
 */
@Path("/api/v1/llm")
@ApplicationScoped
@Tag(name = "LLM Provider", description = "Runtime LLM engine selection (self-host edition)")
@PermitAll
public class LlmProviderResource {

    private static final Logger LOG = LoggerFactory.getLogger(LlmProviderResource.class);

    @Inject
    GetLlmProviderStatusUseCase getStatusUseCase;

    @Inject
    SelectLlmProviderUseCase selectUseCase;

    @ConfigProperty(name = "quorumtrading.llm.selection.enabled", defaultValue = "false")
    boolean selectionEnabled;

    // Where the web UI lives, so the (backend-rendered) setup page can link back into the app.
    // Derived from FRONTEND_URL; the standalone docker stack sets it to the :3000 web UI.
    @ConfigProperty(name = "quorumtrading.frontend.base-url", defaultValue = "http://localhost:3000")
    String frontendBaseUrl;

    @PostConstruct
    void warnIfEnabled() {
        if (selectionEnabled) {
            LOG.warn("LLM provider selection endpoints are ENABLED and anonymous by design — "
                    + "this is only safe in the single-user self-host edition. "
                    + "Never enable quorumtrading.llm.selection.enabled in a multi-user deployment.");
        }
    }

    @jakarta.inject.Inject
    com.quorumtrading.adapter.rest.security.CurrentUserProvider currentUser;

    private void requireEnabled() {
        if (!selectionEnabled) {
            // Feature off (hosted SaaS / default): behave as if the resource does not exist.
            throw new NotFoundException();
        }
    }

    /**
     * Identity gate for the deployment-wide writes below.
     *
     * <p>Selecting the engine and submitting an OpenRouter key were reachable with no identity
     * at all — the only gate was the self-host feature flag, so anyone who could reach the port
     * could repoint the deployment's LLM and store a key. The MCP counterpart
     * ({@code choose_llm_provider}) already requires a token; these two surfaces should agree.</p>
     *
     * <p>Deliberately called <em>after</em> {@link #requireEnabled()} rather than expressed as
     * {@code @Authenticated}: the annotation is enforced before the method body, which would turn
     * the "pretend this resource does not exist" 404 into a 401 that reveals it.</p>
     */
    private void requireCaller() {
        currentUser.getCurrentUserId();
    }

    // --- JSON API -------------------------------------------------------------------------------

    @GET
    @Path("/providers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "LLM provider selection status",
            description = "The active provider (if chosen) and live availability of every selectable "
                    + "provider. Only enabled in the self-host edition.")
    @APIResponse(responseCode = "200", description = "Selection status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = LlmProviderStatusResponse.class)))
    @APIResponse(responseCode = "404", description = "Selection feature disabled on this deployment")
    public Response providers() {
        requireEnabled();
        return Response.ok(LlmProviderStatusResponse.from(getStatusUseCase.get())).build();
    }

    @POST
    @Path("/providers/select")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Select the LLM provider",
            description = "Activates LOCAL, LOCAL_HTTP or OPENROUTER as the deployment's LLM engine, "
                    + "effective on the next AI call — no restart. An OpenRouter key may be submitted "
                    + "inline and is stored encrypted.")
    @APIResponse(responseCode = "200", description = "New selection status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = LlmProviderStatusResponse.class)))
    @APIResponse(responseCode = "400", description = "Unknown provider or provider unavailable",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "404", description = "Selection feature disabled on this deployment")
    public Response select(LlmProviderSelectRequest request) {
        requireEnabled();
        requireCaller();
        LlmProvider provider = parseProvider(request == null ? null : request.provider());
        LlmProviderStatus status;
        try {
            status = selectUseCase.select(new SelectLlmProviderUseCase.Command(
                    provider, request == null ? null : request.apiKey()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
        return Response.ok(LlmProviderStatusResponse.from(status)).build();
    }

    /**
     * Form-encoded variant of select — the setup page's zero-JS forms post here
     * (design: each selectable tile is its own {@code <form method="post">}).
     * Success renders the confirmed page; failure re-renders the picker with the reason.
     */
    @POST
    @Path("/providers/select")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Select the LLM provider (form post from the setup page)")
    public Response selectForm(
            @FormParam("provider") String provider,
            @FormParam("apiKey") String apiKey) {
        requireEnabled();
        requireCaller();
        try {
            LlmProvider parsed = parseProvider(provider);
            LlmProviderStatus status = selectUseCase.select(
                    new SelectLlmProviderUseCase.Command(parsed, apiKey));
            return Response.ok(htmlConfirmed(status)).build();
        } catch (BadRequestException | IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(htmlSetup(getStatusUseCase.get(), e.getMessage()))
                    .build();
        }
    }

    private static LlmProvider parseProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("provider is required: LOCAL, LOCAL_HTTP or OPENROUTER");
        }
        try {
            return LlmProvider.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown provider '" + raw + "'. Use LOCAL, LOCAL_HTTP or OPENROUTER.");
        }
    }

    // --- HTML setup page ------------------------------------------------------------------------

    @GET
    @Path("/setup")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "LLM provider setup page",
            description = "Server-rendered page to choose the LLM engine. Linked from the boot banner.")
    public Response setupPage() {
        requireEnabled();
        return Response.ok(htmlSetup(getStatusUseCase.get(), null)).build();
    }

    // --- HTML rendering ---------------------------------------------------------------------
    // Implements the claude.ai/design "LLM Provider Setup.html" handoff. One <main> per render;
    // the state modifier class and per-tile availability classes drive all styling.

    /** The picker — {@code state-choose}, or {@code state-none} when nothing is usable yet. */
    private String htmlSetup(LlmProviderStatus status, String errorMessage) {
        boolean localAvailable = isAvailable(status, LlmProvider.LOCAL);
        boolean localHttpAvailable = isAvailable(status, LlmProvider.LOCAL_HTTP);
        boolean openRouterAvailable = isAvailable(status, LlmProvider.OPENROUTER);
        boolean nothingSelected = status.selected() == null;

        if (nothingSelected && !localAvailable && !localHttpAvailable && !openRouterAvailable) {
            return htmlNoneAvailable(errorMessage);
        }
        return htmlChoose(status, localAvailable, localHttpAvailable, openRouterAvailable, errorMessage);
    }

    /** STATE · CHOOSE — first run and returning (active provider marked). */
    private String htmlChoose(LlmProviderStatus status, boolean localAvailable,
            boolean localHttpAvailable, boolean openRouterAvailable, String errorMessage) {
        StringBuilder inner = new StringBuilder();
        inner.append("<p class=\"eyebrow\">Local edition setup</p>")
             .append("<h1>Choose your LLM provider</h1>")
             .append("<p class=\"lede\">QuorumTrading needs one AI engine to power analysis and "
                     + "trading agents. You can change this anytime.</p>")
             .append(alert(errorMessage))
             .append("<div class=\"options\">")
             .append(localTile(localAvailable, status.selected() == LlmProvider.LOCAL))
             .append(localHttpTile(localHttpAvailable, status.selected() == LlmProvider.LOCAL_HTTP))
             .append(openRouterTile(openRouterAvailable, status.selected() == LlmProvider.OPENROUTER))
             .append("</div>")
             .append(FOOT_MCP_HINT)
             .append("<p class=\"foot\"><a href=\"").append(frontendBaseUrl)
             .append("\">&larr; Back to QuorumTrading</a></p>");
        return document("state-choose", "main", "polite", inner.toString());
    }

    /** STATE · CONFIRMED — a provider was just selected. */
    private String htmlConfirmed(LlmProviderStatus status) {
        String providerName = providerLabel(status.selected());
        String inner = STATUS_CHECK
                + "<p class=\"eyebrow\">Local edition setup</p>"
                + "<h1>" + providerName + " is now active.</h1>"
                + "<p class=\"lede\">All agents on this installation will use this provider for "
                + "analysis and trading decisions.</p>"
                + "<div class=\"detail\"><div class=\"row\">"
                + "<span class=\"k\">LLM provider</span>"
                + "<span class=\"v\">" + providerName + "</span>"
                + "</div></div>"
                + "<p style=\"margin-top:20px;\"><a class=\"btn btn-primary\" href=\"" + frontendBaseUrl
                + "\">Enter QuorumTrading &rarr;</a></p>"
                + "<p class=\"next\"><span class=\"dot\" aria-hidden=\"true\"></span>"
                + "<span>Driving from an agent instead? You can <strong>close this tab</strong> "
                + "&mdash; the platform is ready.</span></p>"
                + "<p class=\"foot\">Changed your mind? "
                + "<a href=\"/api/v1/llm/setup\">Change provider</a></p>";
        return document("state-confirmed", "status", "polite", inner);
    }

    /** STATE · NONE AVAILABLE — neither engine usable; amber instructional state. */
    private String htmlNoneAvailable(String errorMessage) {
        String inner = STATUS_BANG
                + "<p class=\"eyebrow\">Local edition setup</p>"
                + "<h1>No LLM provider is available yet.</h1>"
                + "<p class=\"lede\">QuorumTrading needs one AI engine before agents can run. "
                + "Make one of these available, then reload this page.</p>"
                + alert(errorMessage)
                + "<ol class=\"steps\">"
                + "<li><span>Start a <strong>local model server</strong> (e.g. <code>ollama serve</code>, "
                + "or LM Studio) &mdash; this page detects it automatically, no key needed. In Docker "
                + "it is reached on your host via <code>host.docker.internal</code>.</span></li>"
                + "<li><span>Or install the <strong>claude CLI</strong> (works when the backend runs "
                + "natively) and make sure <code>claude</code> is on your <code>PATH</code>.</span></li>"
                + "<li><span>Or paste an <strong>OpenRouter API key</strong> below to use hosted "
                + "models instead.</span></li>"
                + "</ol>"
                + "<div class=\"options\" style=\"margin-top: 18px;\">"
                + localTile(false, false)
                + localHttpTile(false, false)
                + openRouterTile(false, false)
                + "</div>"
                + FOOT_MCP_HINT;
        return document("state-none", "alert", "assertive", inner);
    }

    // --- provider tiles -------------------------------------------------------------------------

    private static String localTile(boolean available, boolean active) {
        StringBuilder tile = new StringBuilder();
        if (available) {
            tile.append("<div class=\"opt opt-available").append(active ? " opt-active" : "").append("\">")
                .append("<div class=\"opt-head\">")
                .append("<h2 class=\"opt-name\">Local (<code>claude</code> CLI)</h2>")
                .append(active ? ACTIVE_PILL : badge("badge-ok", "Available"))
                .append("</div>")
                .append("<p class=\"opt-desc\">Runs on your machine via the <code>claude</code> "
                        + "command. No API key, no usage cost.</p>")
                .append("<p class=\"opt-reason\">claude CLI detected</p>")
                .append("<form method=\"post\" action=\"/api/v1/llm/providers/select\">")
                .append("<input type=\"hidden\" name=\"provider\" value=\"local\"/>")
                .append("<button class=\"btn btn-primary\" type=\"submit\">Use local (claude CLI)</button>")
                .append("</form></div>");
        } else {
            tile.append("<div class=\"opt opt-unavailable\" aria-disabled=\"true\">")
                .append("<div class=\"opt-head\">")
                .append("<h2 class=\"opt-name\">Local (<code>claude</code> CLI)</h2>")
                .append(badge("badge-off", "Unavailable"))
                .append("</div>")
                .append("<p class=\"opt-desc\">Runs on your machine via the <code>claude</code> "
                        + "command. No API key, no usage cost.</p>")
                .append("<p class=\"opt-reason\">claude not found on PATH</p>")
                .append("</div>");
        }
        return tile.toString();
    }

    private static String localHttpTile(boolean available, boolean active) {
        StringBuilder tile = new StringBuilder();
        if (available) {
            tile.append("<div class=\"opt opt-available").append(active ? " opt-active" : "").append("\">")
                .append("<div class=\"opt-head\">")
                .append("<h2 class=\"opt-name\">Local server (Ollama / LM Studio)</h2>")
                .append(active ? ACTIVE_PILL : badge("badge-ok", "Reachable"))
                .append("</div>")
                .append("<p class=\"opt-desc\">An OpenAI-compatible server you run yourself. No API "
                        + "key, no usage cost &mdash; and it works from inside Docker.</p>")
                .append("<p class=\"opt-reason\">local server responded</p>")
                .append("<form method=\"post\" action=\"/api/v1/llm/providers/select\">")
                .append("<input type=\"hidden\" name=\"provider\" value=\"local_http\"/>")
                .append("<button class=\"btn btn-primary\" type=\"submit\">Use local server</button>")
                .append("</form></div>");
        } else {
            tile.append("<div class=\"opt opt-unavailable\" aria-disabled=\"true\">")
                .append("<div class=\"opt-head\">")
                .append("<h2 class=\"opt-name\">Local server (Ollama / LM Studio)</h2>")
                .append(badge("badge-off", "Unavailable"))
                .append("</div>")
                .append("<p class=\"opt-desc\">An OpenAI-compatible server you run yourself. No API "
                        + "key, no usage cost &mdash; and it works from inside Docker.</p>")
                .append("<p class=\"opt-reason\">no server at the configured URL</p>")
                .append("</div>");
        }
        return tile.toString();
    }

    private static String openRouterTile(boolean configured, boolean active) {
        StringBuilder tile = new StringBuilder();
        if (configured) {
            tile.append("<div class=\"opt opt-configured").append(active ? " opt-active" : "").append("\">")
                .append("<div class=\"opt-head\">")
                .append("<h2 class=\"opt-name\">OpenRouter</h2>")
                .append(active ? ACTIVE_PILL : badge("badge-ok", "Configured"))
                .append("</div>")
                .append("<p class=\"opt-desc\">Hosted models via your own OpenRouter API key. "
                        + "Usage billed to your account.</p>")
                .append("<p class=\"opt-reason\">key configured</p>")
                .append("<form method=\"post\" action=\"/api/v1/llm/providers/select\">")
                .append("<input type=\"hidden\" name=\"provider\" value=\"openrouter\"/>")
                .append("<button class=\"btn btn-primary\" type=\"submit\">Use OpenRouter</button>")
                .append("</form></div>");
        } else {
            tile.append("<div class=\"opt opt-needskey\">")
                .append("<div class=\"opt-head\">")
                .append("<h2 class=\"opt-name\">OpenRouter</h2>")
                .append(badge("badge-warn", "Needs key"))
                .append("</div>")
                .append("<p class=\"opt-desc\">Hosted models via your own OpenRouter API key. "
                        + "Usage billed to your account.</p>")
                .append("<form method=\"post\" action=\"/api/v1/llm/providers/select\">")
                .append("<input type=\"hidden\" name=\"provider\" value=\"openrouter\"/>")
                .append("<div class=\"key-field\">")
                .append("<label for=\"or-key\">OpenRouter API key</label>")
                .append("<input id=\"or-key\" name=\"apiKey\" type=\"password\" required "
                        + "autocomplete=\"off\" spellcheck=\"false\" placeholder=\"sk-or-&hellip;\"/>")
                .append("</div>")
                .append("<p class=\"key-hint\">Stored encrypted in your local database. "
                        + "Only ever sent to OpenRouter.</p>")
                .append("<button class=\"btn btn-primary\" type=\"submit\">Use OpenRouter</button>")
                .append("</form></div>");
        }
        return tile.toString();
    }

    private static String badge(String kind, String label) {
        return "<span class=\"badge " + kind + "\">"
                + "<span class=\"b-dot\" aria-hidden=\"true\"></span>" + label + "</span>";
    }

    private static String alert(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return "<p class=\"alert\" role=\"alert\">" + escape(message) + "</p>";
    }

    private static String providerLabel(LlmProvider provider) {
        return switch (provider) {
            case LOCAL -> "Local (claude CLI)";
            case LOCAL_HTTP -> "Local server (Ollama / OpenAI-compatible)";
            case OPENROUTER -> "OpenRouter";
        };
    }

    private static boolean isAvailable(LlmProviderStatus status, LlmProvider provider) {
        return status.providers().stream()
                .anyMatch(p -> p.provider() == provider && p.available());
    }

    // --- document shell (design HEAD + stage + card) ----------------------------------------

    /** Wraps one state's body in the full document shell (head + style + centred card). */
    private static String document(String stateClass, String role, String ariaLive, String inner) {
        return HEAD
                + "<body><div class=\"stage\">"
                + "<main class=\"setup-card " + stateClass + "\" role=\"" + role
                + "\" aria-live=\"" + ariaLive + "\">"
                + "<div class=\"accent-bar\"></div><div class=\"body\">"
                + WORDMARK
                + inner
                + "</div></main></div></body></html>";
    }

    private static final String ACTIVE_PILL = "<span class=\"active-pill\">Currently active</span>";

    private static final String STATUS_CHECK =
            "<div class=\"status\" aria-hidden=\"true\">"
            + "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
            + "stroke-width=\"2.4\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
            + "<path d=\"M5 12.5 L10 17.5 L19 7\"/></svg></div>";

    private static final String STATUS_BANG =
            "<div class=\"status\" aria-hidden=\"true\"><span class=\"glyph\">!</span></div>";

    /**
     * Footer line. The design links a {@code /help/llm-setup} guide that doesn't exist in any
     * edition — deviation: point agentic users at the MCP tool instead.
     */
    private static final String FOOT_MCP_HINT =
            "<p class=\"foot\">Agents can also choose via the "
            + "<span class=\"mono\">choose_llm_provider</span> MCP tool.</p>";

    private static final String WORDMARK =
            "<div class=\"wordmark\" aria-label=\"QuorumTrading\">"
            + "<span class=\"mark\" aria-hidden=\"true\">"
            + "<svg viewBox=\"0 0 20 20\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.4\">"
            + "<circle cx=\"10\" cy=\"4\" r=\"2.2\" fill=\"#00ff41\" stroke=\"none\"/>"
            + "<circle cx=\"4\" cy=\"15\" r=\"2.2\" fill=\"#00d9ff\" stroke=\"none\"/>"
            + "<circle cx=\"16\" cy=\"15\" r=\"2.2\" fill=\"#ffb000\" stroke=\"none\"/>"
            + "<path d=\"M10 6.2 L5.6 13M10 6.2 L14.4 13M6.2 15 L13.8 15\" stroke=\"#5a6a76\" "
            + "stroke-width=\"0.8\" stroke-linecap=\"round\"/></svg></span>"
            + "<span>Quorum<span class=\"accent\">Trading</span></span></div>";

    /** Design CSS verbatim (review-only chrome removed; {@code .alert} added for select errors). */
    private static final String HEAD = """
            <!doctype html><html lang="en"><head><meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <meta name="robots" content="noindex"/>
            <title>QuorumTrading &mdash; Choose your LLM provider</title>
            <link rel="preconnect" href="https://fonts.googleapis.com"/>
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
            <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap" rel="stylesheet"/>
            <style>
            :root{
              --bg:#0a0e14; --surface:#141a23; --surface-2:#1e2632; --border:#2a3344; --border-subtle:#1a2030;
              --fg:#e6e6e6; --fg-strong:#ffffff; --fg-muted:#8a98a4; --fg-dimmed:#5a6a76; --fg-subtle:#4a5562;
              --brand:#00ff41; --success:#06ffa5; --amber:#ffb000; --focus:#00d9ff;
              --font-sans:"IBM Plex Sans",system-ui,-apple-system,"Segoe UI",Helvetica,Arial,sans-serif;
              --font-mono:"IBM Plex Mono","SF Mono","Courier New",Courier,monospace;
              --radius-sm:2px; --radius-md:4px; --radius-lg:8px;
            }
            *{box-sizing:border-box;} html,body{margin:0;padding:0;}
            body{
              background:var(--bg); color:var(--fg); font-family:var(--font-sans); line-height:1.5;
              -webkit-font-smoothing:antialiased; -moz-osx-font-smoothing:grayscale;
              background-image:radial-gradient(1200px 600px at 50% -10%,rgba(0,217,255,0.04),transparent 60%),
                radial-gradient(900px 500px at 50% 120%,rgba(0,255,65,0.03),transparent 60%);
              background-repeat:no-repeat;
            }
            .stage{min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;padding:40px 20px;}
            .wordmark{display:inline-flex;align-items:center;gap:9px;font-size:18px;font-weight:600;color:var(--fg-strong);letter-spacing:-0.01em;}
            .wordmark .mark{width:22px;height:22px;display:inline-flex;}
            .wordmark .mark svg{width:100%;height:100%;}
            .wordmark .accent{color:var(--brand);}
            .setup-card{
              width:100%;max-width:520px;background:var(--surface);border:1px solid var(--border);
              border-radius:var(--radius-lg);box-shadow:0 4px 12px rgba(0,0,0,0.2),0 18px 50px rgba(0,0,0,0.35);
              overflow:hidden;--accent:var(--focus);--accent-soft:rgba(0,217,255,0.08);--accent-line:rgba(0,217,255,0.28);
            }
            .state-confirmed{--accent:var(--success);--accent-soft:rgba(6,255,165,0.10);--accent-line:rgba(6,255,165,0.30);}
            .state-none{--accent:var(--amber);--accent-soft:rgba(255,176,0,0.10);--accent-line:rgba(255,176,0,0.30);}
            .setup-card .accent-bar{height:2px;background:var(--accent);opacity:0.9;}
            .setup-card .body{padding:30px 32px 28px;}
            .status{width:52px;height:52px;border-radius:50%;display:flex;align-items:center;justify-content:center;
              background:var(--accent-soft);border:1px solid var(--accent-line);color:var(--accent);margin:22px 0 18px;}
            .status svg{width:24px;height:24px;display:block;}
            .status .glyph{font-family:var(--font-mono);font-size:26px;font-weight:600;line-height:1;}
            .eyebrow{font-family:var(--font-mono);font-size:11px;letter-spacing:0.14em;text-transform:uppercase;color:var(--fg-dimmed);margin:22px 0 10px;}
            .status + .eyebrow{margin-top:0;}
            .setup-card h1{margin:0 0 10px;font-size:23px;line-height:1.25;font-weight:600;color:var(--fg-strong);letter-spacing:-0.01em;text-wrap:balance;}
            .lede{margin:0;font-size:15px;line-height:1.6;color:var(--fg-muted);max-width:46ch;}
            .alert{margin:14px 0 0;font-size:13.5px;line-height:1.55;color:var(--fg);padding:11px 14px;
              background:rgba(255,176,0,0.06);border:1px solid rgba(255,176,0,0.25);border-left:2px solid var(--amber);border-radius:var(--radius-sm);}
            .options{display:flex;flex-direction:column;gap:12px;margin-top:22px;}
            .opt{border:1px solid var(--border-subtle);border-radius:var(--radius-md);background:var(--bg);padding:16px 16px 14px;}
            .opt-available:hover,.opt-configured:hover{border-color:var(--border);}
            .opt-head{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;}
            .opt-name{margin:0;font-size:15.5px;font-weight:600;color:var(--fg-strong);letter-spacing:-0.005em;}
            .opt-name code{font-family:var(--font-mono);font-size:13.5px;font-weight:500;color:var(--fg);
              background:var(--surface-2);border:1px solid var(--border-subtle);border-radius:var(--radius-sm);padding:1px 5px;}
            .opt-desc{margin:6px 0 0;font-size:13.5px;line-height:1.55;color:var(--fg-muted);max-width:52ch;}
            .opt-desc code{font-family:var(--font-mono);font-size:12.5px;color:var(--fg);}
            .badge{display:inline-flex;align-items:center;gap:7px;font-family:var(--font-mono);font-size:10.5px;font-weight:500;
              letter-spacing:0.12em;text-transform:uppercase;padding:4px 9px;border-radius:999px;border:1px solid var(--border);
              color:var(--fg-muted);white-space:nowrap;}
            .badge .b-dot{width:6px;height:6px;border-radius:50%;background:currentColor;flex:0 0 auto;}
            .badge-ok{color:var(--success);border-color:rgba(6,255,165,0.30);background:rgba(6,255,165,0.07);}
            .badge-warn{color:var(--amber);border-color:rgba(255,176,0,0.30);background:rgba(255,176,0,0.07);}
            .badge-off{color:var(--fg-dimmed);border-color:var(--border-subtle);background:transparent;}
            .opt-reason{margin:7px 0 0;font-family:var(--font-mono);font-size:11.5px;letter-spacing:0.02em;color:var(--fg-dimmed);}
            .opt-unavailable{opacity:0.55;}
            .opt-unavailable .opt-name{color:var(--fg-muted);}
            .active-pill{display:inline-flex;align-items:center;gap:7px;font-family:var(--font-mono);font-size:10.5px;font-weight:600;
              letter-spacing:0.12em;text-transform:uppercase;padding:4px 9px;border-radius:999px;color:#0a0e14;background:var(--success);white-space:nowrap;}
            .opt-active{border-color:rgba(6,255,165,0.35);}
            .opt form{margin:14px 0 0;display:flex;flex-direction:column;gap:10px;}
            .opt-active form{display:none;}
            .key-field{display:flex;flex-direction:column;gap:6px;}
            .key-field label{font-family:var(--font-mono);font-size:10.5px;letter-spacing:0.1em;text-transform:uppercase;color:var(--fg-dimmed);}
            .key-field input{font-family:var(--font-mono);font-size:13px;color:var(--fg-strong);background:var(--surface);
              border:1px solid var(--border);border-radius:var(--radius-sm);padding:9px 11px;width:100%;letter-spacing:0.02em;}
            .key-field input::placeholder{color:var(--fg-subtle);}
            .key-field input:focus-visible{outline:2px solid var(--focus);outline-offset:1px;border-color:var(--focus);}
            .key-hint{margin:0;font-size:12px;line-height:1.5;color:var(--fg-dimmed);}
            .btn{appearance:none;display:inline-block;text-decoration:none;font-family:var(--font-sans);font-size:13.5px;font-weight:600;letter-spacing:0.005em;
              border-radius:var(--radius-sm);padding:9px 16px;cursor:pointer;border:1px solid transparent;align-self:flex-start;
              transition:background-color 120ms ease,border-color 120ms ease;}
            .btn-primary{background:var(--brand);color:#0a0e14;}
            .btn-primary:hover{background:#33ff67;}
            .btn-primary:active{background:#00e63a;}
            .btn:focus-visible{outline:2px solid var(--focus);outline-offset:2px;}
            .detail{margin-top:20px;border:1px solid var(--border-subtle);border-radius:var(--radius-md);background:var(--bg);overflow:hidden;}
            .detail .row{display:flex;align-items:baseline;justify-content:space-between;gap:16px;padding:11px 14px;}
            .detail .k{font-family:var(--font-mono);font-size:10.5px;letter-spacing:0.1em;text-transform:uppercase;color:var(--fg-dimmed);white-space:nowrap;}
            .detail .v{font-size:14px;color:var(--fg-strong);font-weight:500;text-align:right;min-width:0;word-break:break-word;}
            .next{display:flex;align-items:flex-start;gap:9px;margin-top:22px;padding-top:18px;border-top:1px solid var(--border-subtle);
              font-size:13.5px;line-height:1.55;color:var(--fg-muted);}
            .next .dot{flex:0 0 auto;width:6px;height:6px;border-radius:50%;background:var(--accent);margin-top:6px;}
            .next strong{color:var(--fg);font-weight:600;}
            .foot{margin-top:18px;font-family:var(--font-mono);font-size:11.5px;letter-spacing:0.04em;color:var(--fg-dimmed);}
            .foot a{color:var(--focus);text-decoration:none;border-radius:2px;}
            .foot a:hover{text-decoration:underline;text-underline-offset:2px;}
            .foot .mono{font-family:var(--font-mono);color:var(--fg);}
            .steps{margin:18px 0 0;padding:0;list-style:none;display:flex;flex-direction:column;gap:10px;counter-reset:step;}
            .steps li{display:flex;gap:11px;align-items:flex-start;font-size:13.5px;line-height:1.55;color:var(--fg-muted);}
            .steps li::before{counter-increment:step;content:counter(step,decimal-leading-zero);font-family:var(--font-mono);
              font-size:11px;color:var(--amber);border:1px solid rgba(255,176,0,0.30);border-radius:var(--radius-sm);
              padding:1px 6px;margin-top:2px;flex:0 0 auto;}
            .steps code{font-family:var(--font-mono);font-size:12.5px;color:var(--fg);background:var(--surface-2);
              border:1px solid var(--border-subtle);border-radius:var(--radius-sm);padding:1px 5px;}
            .steps strong{color:var(--fg);font-weight:600;}
            a:focus-visible,:focus-visible{outline:2px solid var(--focus);outline-offset:2px;border-radius:2px;}
            a:focus{outline:none;}
            @media (max-width:480px){
              .stage{padding:28px 14px;}
              .setup-card .body{padding:24px 18px 22px;}
              .setup-card h1{font-size:21px;}
              .opt{padding:14px 13px 12px;}
              .btn{align-self:stretch;text-align:center;}
              .detail .row{flex-direction:column;align-items:flex-start;gap:3px;}
              .detail .v{text-align:left;}
            }
            </style></head>""";

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
