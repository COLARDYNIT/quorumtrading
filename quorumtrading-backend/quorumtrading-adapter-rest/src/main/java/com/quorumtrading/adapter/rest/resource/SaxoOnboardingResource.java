package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.SaxoOnboardingStartRequest;
import com.quorumtrading.adapter.rest.dto.response.SaxoOnboardingStartResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase;
import com.quorumtrading.application.usecase.saxo.OnboardingStart;
import com.quorumtrading.application.usecase.saxo.StartSaxoOnboardingUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
 * REST edge for the Saxo Bank (EU) OAuth onboarding flow.
 *
 * <p>Links a user's <b>personal</b> Saxo login to a {@link BrokerAccount} through the interactive
 * authorization-code flow. The heavy lifting lives in the application layer
 * ({@link StartSaxoOnboardingUseCase} / {@link CompleteSaxoOnboardingUseCase}); this adapter only
 * translates HTTP ⇄ use case and renders the browser landing page on callback.</p>
 *
 * <p><b>Two endpoints, two audiences:</b></p>
 * <ul>
 *   <li>{@code POST /start} — called by the user/agent (authenticated). Returns the Saxo consent
 *       URL as JSON; the caller opens it in a browser.</li>
 *   <li>{@code GET /callback} — called by Saxo's redirect (a browser, <b>not</b> authenticated by a
 *       session). Security comes from the single-use {@code state} minted at {@code start}, not from
 *       a session cookie — so this endpoint is intentionally anonymous and renders HTML.</li>
 * </ul>
 *
 * <p><b>SIM-first (local edition v1):</b> only {@code SIM} onboarding is enabled. A {@code LIVE}
 * request is rejected with {@code 400} until live onboarding is signed off — see
 * {@code docs/operations/SAXO_READINESS.md} §7 step 3.</p>
 */
@Path("/api/v1/broker-accounts/saxo/onboard")
@ApplicationScoped
@Tag(name = "Saxo Onboarding", description = "Link a personal Saxo account via OAuth")
@PermitAll
public class SaxoOnboardingResource {

    private static final Logger LOG = LoggerFactory.getLogger(SaxoOnboardingResource.class);

    @Inject
    StartSaxoOnboardingUseCase startSaxoOnboardingUseCase;

    @Inject
    CompleteSaxoOnboardingUseCase completeSaxoOnboardingUseCase;

    @Inject
    CurrentUserProvider currentUser;

    /**
     * The redirect URI Saxo calls back after consent. Must exactly match the redirect URI registered
     * on the Saxo OAuth app, and is replayed verbatim on the token exchange.
     */
    @ConfigProperty(
            name = "quorumtrading.broker.saxo.redirect-uri",
            defaultValue = "http://localhost:8085/api/v1/broker-accounts/saxo/onboard/callback")
    String redirectUri;

    /** Frontend origin used to build absolute links back into the web app from this backend page. */
    @ConfigProperty(name = "quorumtrading.frontend.base-url", defaultValue = "http://localhost:3000")
    String frontendBaseUrl;

    /**
     * Whether a web UI is actually served alongside this backend. The self-host (local) edition runs
     * headless — there is no page at {@code /settings/broker-accounts}, so linking there would 404.
     * When {@code false} the result page directs the user back to their agent instead of a dead link.
     */
    @ConfigProperty(name = "quorumtrading.frontend.enabled", defaultValue = "true")
    boolean frontendEnabled;

    /**
     * Master gate: when the Saxo integration is off (every SaaS deployment until the broker
     * approves hosted integration), these endpoints 404 — same disappear-when-disabled design
     * as the LLM-selection endpoints. Without this gate an authenticated user could mint
     * consent URLs and onboarding-state rows against placeholder credentials.
     */
    @ConfigProperty(name = "quorumtrading.broker.saxo.enabled", defaultValue = "false")
    boolean saxoEnabled;

    private void requireSaxoEnabled() {
        if (!saxoEnabled) {
            throw new NotFoundException("Saxo integration is not offered on this instance.");
        }
    }

    @POST
    @Path("/start")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Begin Saxo onboarding",
            description = "Mints a Saxo consent URL and single-use state for the current user. Open the "
                    + "returned authorizationUrl in a browser to grant consent. Only SIM is enabled in "
                    + "the local edition.")
    @APIResponse(
            responseCode = "200",
            description = "Consent URL and single-use state",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = SaxoOnboardingStartResponse.class)))
    @APIResponse(
            responseCode = "400",
            description = "Unsupported environment (e.g. LIVE) or invalid request",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProblemDetails.class)))
    public Response start(SaxoOnboardingStartRequest request) {
        requireSaxoEnabled();
        UserId userId = currentUser.getCurrentUserId();

        String env = request == null || request.environment() == null || request.environment().isBlank()
                ? "SIM"
                : request.environment().trim().toUpperCase();

        if (!"SIM".equals(env)) {
            // SIM-first: LIVE onboarding is deliberately gated off in the local edition v1.
            throw new BadRequestException(
                    "Only SIM onboarding is enabled in the local edition. LIVE onboarding is not yet available.");
        }

        StartSaxoOnboardingUseCase.Command command = new StartSaxoOnboardingUseCase.Command(
                userId,
                BrokerType.SAXO_SIM,
                SaxoEnvironment.SIM,
                redirectUri,
                false);

        OnboardingStart onboardingStart;
        try {
            onboardingStart = startSaxoOnboardingUseCase.start(command);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }

        LOG.info("Issued Saxo onboarding consent URL for user {}", userId.value());
        return Response.ok(SaxoOnboardingStartResponse.from(onboardingStart)).build();
    }

    /**
     * Saxo redirects the user's browser here after consent. Anonymous by design — the single-use
     * {@code state} is the authenticator. Renders an HTML landing page (success or error).
     */
    @GET
    @Path("/callback")
    @Produces(MediaType.TEXT_HTML)
    @Operation(
            summary = "Saxo OAuth callback",
            description = "Redirect target for Saxo consent. Exchanges the authorization code for the "
                    + "rotating token pair and creates the broker account. Renders an HTML landing page.")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @QueryParam("error_description") String errorDescription) {

        // No consent URL can ever have been minted while disabled — anything arriving here is noise.
        requireSaxoEnabled();

        // Consent denied / Saxo-side error: render the reason, do not touch the use case.
        if (error != null && !error.isBlank()) {
            LOG.warn("Saxo onboarding callback returned error: {}", error);
            String detail = errorDescription != null && !errorDescription.isBlank()
                    ? errorDescription
                    : null;
            return htmlDenied(detail);
        }

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return htmlError("Missing authorization code or state. The link may have been opened directly "
                    + "rather than via the Saxo redirect.");
        }

        BrokerAccount account;
        try {
            account = completeSaxoOnboardingUseCase.complete(
                    new CompleteSaxoOnboardingUseCase.Command(state, code));
        } catch (IllegalArgumentException e) {
            LOG.warn("Saxo onboarding callback rejected: {}", e.getMessage());
            return htmlError(e.getMessage());
        }

        LOG.info("Linked Saxo account {} via onboarding callback", account.getId().value());
        return htmlSuccess(account);
    }

    // --- HTML landing pages -------------------------------------------------------------------
    // Recreates the "Account Linking Result" design (claude.ai/design handoff): a centred dark
    // card with three states selected by a modifier class on <main class="link-card">. Copy is
    // provider-neutral by design ("brokerage account", never the broker name).

    private Response htmlSuccess(BrokerAccount account) {
        String inner = STATUS_CHECK
                + "<p class=\"eyebrow\">Account linking</p>"
                + "<h1>Your brokerage account is linked.</h1>"
                + "<p class=\"lede\">QuorumTrading can now place and manage trades on this account "
                + "through your agent. No further action is needed here.</p>"
                + "<div class=\"account\">"
                + "<div class=\"row\"><span class=\"k\">Account</span>"
                + "<span class=\"v\">" + escape(account.getName()) + "</span></div>"
                + "<div class=\"row\"><span class=\"k\">Account ID</span>"
                + "<span class=\"v mono\">" + escape(account.getId().value().toString()) + "</span></div>"
                + "</div>"
                + "<p class=\"next\"><span class=\"dot\" aria-hidden=\"true\"></span>"
                + "<span>You can <strong>close this tab</strong> and return to your agent "
                + "&mdash; the account is ready to use.</span></p>"
                + manageAccountsFooter();
        return Response.ok(document("state-success", "status", "polite", inner)).build();
    }

    private Response htmlError(String message) {
        String inner = STATUS_BANG
                + "<p class=\"eyebrow\">Account linking</p>"
                + "<h1>We couldn&rsquo;t link your account.</h1>"
                + "<p class=\"lede\">The connection didn&rsquo;t complete, so nothing was linked "
                + "and no access was granted.</p>"
                + "<p class=\"reason\">" + escape(message) + "</p>"
                + "<p class=\"next\"><span class=\"dot\" aria-hidden=\"true\"></span>"
                + "<span>Return to your agent and <strong>start the linking process again</strong>. "
                + "If it keeps failing, try once more in a few minutes.</span></p>";
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(document("state-error", "alert", "assertive", inner)).build();
    }

    private Response htmlDenied(String detail) {
        String reason = (detail == null || detail.isBlank())
                ? "You declined the permission request at your brokerage, or the request timed out "
                  + "before it was approved."
                : detail;
        String inner = STATUS_BANG
                + "<p class=\"eyebrow\">Account linking</p>"
                + "<h1>Linking was cancelled.</h1>"
                + "<p class=\"lede\">Authorization wasn&rsquo;t granted, so QuorumTrading has no "
                + "access to your brokerage account.</p>"
                + "<p class=\"reason\">" + escape(reason) + "</p>"
                + "<p class=\"next\"><span class=\"dot\" aria-hidden=\"true\"></span>"
                + "<span>If this wasn&rsquo;t intentional, return to your agent and "
                + "<strong>start again</strong>, then approve the access request to finish linking.</span></p>";
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(document("state-denied", "alert", "assertive", inner)).build();
    }

    /**
     * Footer line on the success page. With a web UI present, links to the account-management page;
     * in the headless self-host edition there is no such page, so it points the user back to their
     * agent rather than rendering a link that 404s.
     */
    private String manageAccountsFooter() {
        if (frontendEnabled) {
            return "<p class=\"foot\">Linked the wrong account? "
                    + "<a href=\"" + manageAccountsHref() + "\">Manage linked accounts</a></p>";
        }
        return "<p class=\"foot\">Linked the wrong account? "
                + "Ask your agent to unlink it, or start the linking process again.</p>";
    }

    private String manageAccountsHref() {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        return escape(base + "/settings/broker-accounts");
    }

    /** Wraps one state's body in the full document shell (head + style + centred card). */
    private static String document(String stateClass, String role, String ariaLive, String inner) {
        return HEAD
                + "<body><div class=\"stage\">"
                + "<main class=\"link-card " + stateClass + "\" role=\"" + role
                + "\" aria-live=\"" + ariaLive + "\">"
                + "<div class=\"accent-bar\"></div><div class=\"body\">"
                + WORDMARK
                + inner
                + "</div></main></div></body></html>";
    }

    private static final String STATUS_CHECK =
            "<div class=\"status\" aria-hidden=\"true\">"
            + "<svg class=\"ic-check\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
            + "stroke-width=\"2.4\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
            + "<path d=\"M5 12.5 L10 17.5 L19 7\"/></svg></div>";

    private static final String STATUS_BANG =
            "<div class=\"status\" aria-hidden=\"true\"><span class=\"glyph ic-bang\">!</span></div>";

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

    private static final String HEAD = """
            <!doctype html><html lang="en"><head><meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <meta name="robots" content="noindex"/>
            <title>QuorumTrading &mdash; Account linking</title>
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
            .link-card{
              width:100%;max-width:460px;background:var(--surface);border:1px solid var(--border);
              border-radius:var(--radius-lg);box-shadow:0 4px 12px rgba(0,0,0,0.2),0 18px 50px rgba(0,0,0,0.35);
              overflow:hidden;--accent:var(--success);--accent-soft:rgba(6,255,165,0.10);--accent-line:rgba(6,255,165,0.30);
            }
            .state-error,.state-denied{--accent:var(--amber);--accent-soft:rgba(255,176,0,0.10);--accent-line:rgba(255,176,0,0.30);}
            .link-card .accent-bar{height:2px;background:var(--accent);opacity:0.9;}
            .link-card .body{padding:30px 32px 28px;}
            .status{width:52px;height:52px;border-radius:50%;display:flex;align-items:center;justify-content:center;
              background:var(--accent-soft);border:1px solid var(--accent-line);color:var(--accent);margin:22px 0 18px;}
            .status svg{width:24px;height:24px;display:block;}
            .status .glyph{font-family:var(--font-mono);font-size:26px;font-weight:600;line-height:1;}
            .status .ic-check{display:none;} .status .ic-bang{display:none;}
            .state-success .status .ic-check{display:block;}
            .state-error .status .ic-bang,.state-denied .status .ic-bang{display:block;}
            .eyebrow{font-family:var(--font-mono);font-size:11px;letter-spacing:0.14em;text-transform:uppercase;color:var(--fg-dimmed);margin-bottom:10px;}
            .link-card h1{margin:0 0 10px;font-size:23px;line-height:1.25;font-weight:600;color:var(--fg-strong);letter-spacing:-0.01em;text-wrap:balance;}
            .lede{margin:0;font-size:15px;line-height:1.6;color:var(--fg-muted);max-width:42ch;}
            .reason{margin:14px 0 0;font-size:14.5px;line-height:1.55;color:var(--fg);padding:11px 14px;
              background:var(--bg);border:1px solid var(--border-subtle);border-left:2px solid var(--accent);border-radius:var(--radius-sm);}
            .account{margin-top:20px;border:1px solid var(--border-subtle);border-radius:var(--radius-md);background:var(--bg);overflow:hidden;}
            .account .row{display:flex;align-items:baseline;justify-content:space-between;gap:16px;padding:11px 14px;}
            .account .row + .row{border-top:1px solid var(--border-subtle);}
            .account .k{font-family:var(--font-mono);font-size:10.5px;letter-spacing:0.1em;text-transform:uppercase;color:var(--fg-dimmed);white-space:nowrap;}
            .account .v{font-size:14px;color:var(--fg-strong);font-weight:500;text-align:right;min-width:0;word-break:break-word;}
            .account .v.mono{font-family:var(--font-mono);font-weight:400;font-variant-numeric:tabular-nums;letter-spacing:0.01em;}
            .next{display:flex;align-items:flex-start;gap:9px;margin-top:22px;padding-top:18px;border-top:1px solid var(--border-subtle);font-size:13.5px;line-height:1.55;color:var(--fg-muted);}
            .next .dot{flex:0 0 auto;width:6px;height:6px;border-radius:50%;background:var(--accent);margin-top:6px;}
            .next strong{color:var(--fg);font-weight:600;}
            .foot{margin-top:18px;font-family:var(--font-mono);font-size:11.5px;letter-spacing:0.04em;color:var(--fg-dimmed);}
            .foot a{color:var(--focus);text-decoration:none;border-radius:2px;}
            .foot a:hover{text-decoration:underline;text-underline-offset:2px;}
            a:focus-visible,:focus-visible{outline:2px solid var(--focus);outline-offset:2px;border-radius:2px;}
            a:focus{outline:none;}
            @media (max-width:480px){
              .stage{padding:28px 14px;}
              .link-card .body{padding:24px 20px 22px;}
              .link-card h1{font-size:21px;}
              .account .row{flex-direction:column;align-items:flex-start;gap:3px;}
              .account .v{text-align:left;}
            }
            @media (prefers-reduced-motion:no-preference){
              .link-card{animation:card-in 320ms ease;}
              @keyframes card-in{from{transform:translateY(8px);}to{transform:none;}}
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
