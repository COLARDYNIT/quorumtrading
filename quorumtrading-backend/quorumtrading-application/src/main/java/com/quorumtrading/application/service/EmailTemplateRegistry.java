package com.quorumtrading.application.service;

import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationType;

/**
 * Builds subject + plaintext + HTML bodies for outgoing notification emails.
 *
 * <p>Templates are inline Java rather than externalised resource files for v1 —
 * v1 ships with five notification types and small bodies, and inline lets us
 * type-check the template against the {@link Notification#getPayload()} map at
 * compile time. If templates grow beyond a screen each, this is the place to
 * switch to Mustache or a templating engine.</p>
 *
 * <p>The unsubscribe link is appended to both plaintext and HTML bodies — every
 * outgoing email must offer per-type opt-out as required by CAN-SPAM and GDPR.</p>
 */
public class EmailTemplateRegistry {

    public Rendered render(Notification n, String unsubscribeUrl, String appBaseUrl) {
        return switch (n.getType()) {
            case INVESTMENT_EXECUTED -> investmentExecuted(n, unsubscribeUrl, appBaseUrl);
            case INVESTMENT_SKIPPED -> investmentSkipped(n, unsubscribeUrl, appBaseUrl);
            case INVESTMENT_FAILED -> investmentFailed(n, unsubscribeUrl, appBaseUrl);
            case PLAN_PAUSED -> planPaused(n, unsubscribeUrl, appBaseUrl);
            case SIMULATION_DECISION -> simulationDecision(n, unsubscribeUrl, appBaseUrl);
            case SIMULATION_PAUSED_BUDGET -> simulationPausedBudget(n, unsubscribeUrl, appBaseUrl);
            case SIMULATION_LOW_BUDGET -> simulationLowBudget(n, unsubscribeUrl, appBaseUrl);
            case MARKET_RESEARCH_STRATEGY_CREATED -> marketResearchStrategyCreated(n, unsubscribeUrl, appBaseUrl);
            // Re-auth uses the generic template for now: the dispatcher supplies a clear
            // title/body and the dedicated template is a later-wave concern.
            case BROKER_REAUTH_REQUIRED -> generic(n, unsubscribeUrl, appBaseUrl);
            case GENERIC -> generic(n, unsubscribeUrl, appBaseUrl);
        };
    }

    public record Rendered(String subject, String plainText, String html) {}

    // ---- Per-type templates ----

    private Rendered investmentExecuted(Notification n, String unsubUrl, String appBaseUrl) {
        String simulationLink = simulationLink(n, appBaseUrl);
        String amount = string(n, "actualAmount", "");
        String currency = string(n, "currency", "USD");
        String subject = "QuorumTrading: your investment plan funded "
                + (amount.isEmpty() ? "successfully" : currency + " " + amount);
        String plain = """
                %s

                We funded your scheduled investment%s. You can review the allocation and the
                AI picker's reasoning in your dashboard:

                %s

                — QuorumTrading

                %s
                """.formatted(
                n.getTitle(),
                amount.isEmpty() ? "" : " for " + currency + " " + amount,
                allocationLink(n, appBaseUrl),
                unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), n.getBody(), simulationLink, unsubUrl,
                "Open allocation", allocationLink(n, appBaseUrl));
        return new Rendered(subject, plain, html);
    }

    private Rendered investmentSkipped(Notification n, String unsubUrl, String appBaseUrl) {
        String subject = "QuorumTrading: your investment plan skipped a contribution";
        String reason = string(n, "failureReason", n.getBody() == null ? "see your dashboard for details" : n.getBody());
        String plain = """
                %s

                Reason: %s

                Your plan will try again on the next scheduled date. You can review the
                full audit trail here:

                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(), reason, allocationLink(n, appBaseUrl), unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), reason, null, unsubUrl,
                "Open allocation", allocationLink(n, appBaseUrl));
        return new Rendered(subject, plain, html);
    }

    private Rendered investmentFailed(Notification n, String unsubUrl, String appBaseUrl) {
        String subject = "QuorumTrading: investment plan execution failed";
        String reason = string(n, "failureReason", n.getBody() == null ? "broker error" : n.getBody());
        String plain = """
                %s

                Your scheduled investment could not be funded. Details:
                %s

                Review the allocation and investigate:
                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(), reason, allocationLink(n, appBaseUrl), unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), reason, null, unsubUrl,
                "Open allocation", allocationLink(n, appBaseUrl));
        return new Rendered(subject, plain, html);
    }

    private Rendered planPaused(Notification n, String unsubUrl, String appBaseUrl) {
        String subject = "QuorumTrading: investment plan paused";
        String plan = string(n, "planId", "");
        String plain = """
                %s

                %s

                Resume the plan or adjust its settings in your dashboard:
                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(),
                n.getBody() == null ? "" : n.getBody(),
                plan.isEmpty() ? appBaseUrl + "/investment-plans"
                        : appBaseUrl + "/investment-plans/" + plan,
                unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(),
                n.getBody() == null ? "" : n.getBody(),
                null, unsubUrl,
                "Open investment plans", appBaseUrl + "/investment-plans");
        return new Rendered(subject, plain, html);
    }

    private Rendered simulationDecision(Notification n, String unsubUrl, String appBaseUrl) {
        String action = string(n, "action", "TRADE");
        String symbol = string(n, "symbol", "");
        String quantity = string(n, "quantity", "");
        String price = string(n, "price", "");
        String currency = string(n, "currency", "USD");
        String simLink = simulationLink(n, appBaseUrl);
        String subject = "QuorumTrading: " + action
                + (symbol.isEmpty() ? "" : " " + symbol)
                + " executed";
        String details = action + (symbol.isEmpty() ? "" : " " + symbol)
                + (quantity.isEmpty() ? "" : " × " + quantity)
                + (price.isEmpty() ? "" : " @ " + currency + " " + price);
        String plain = """
                %s

                Your simulation just executed: %s

                Review the full decision and the agents' reasoning:
                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(), details,
                simLink == null ? appBaseUrl : simLink,
                unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), details, simLink, unsubUrl,
                "Open simulation", simLink == null ? appBaseUrl : simLink);
        return new Rendered(subject, plain, html);
    }

    private Rendered simulationPausedBudget(Notification n, String unsubUrl, String appBaseUrl) {
        String subject = "QuorumTrading: simulation paused — budget exhausted";
        String reason = string(n, "pauseReason", n.getBody() == null ? "budget depleted" : n.getBody());
        String simLink = simulationLink(n, appBaseUrl);
        String plain = """
                %s

                Your simulation stopped running because the LLM spend budget is exhausted.
                Reason: %s

                Top up the budget or adjust the daily cap to resume:
                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(), reason,
                simLink == null ? appBaseUrl : simLink,
                unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), reason, simLink, unsubUrl,
                "Open simulation", simLink == null ? appBaseUrl : simLink);
        return new Rendered(subject, plain, html);
    }

    private Rendered simulationLowBudget(Notification n, String unsubUrl, String appBaseUrl) {
        String subject = "QuorumTrading: simulation budget running low";
        String remaining = string(n, "remainingPct", "");
        String body = remaining.isEmpty()
                ? "Your simulation's LLM budget is running low — top it up to avoid an unexpected pause."
                : "Your simulation has " + remaining + "% of its LLM budget left — top it up to avoid an unexpected pause.";
        String simLink = simulationLink(n, appBaseUrl);
        String plain = """
                %s

                %s

                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(), body,
                simLink == null ? appBaseUrl : simLink,
                unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), body, simLink, unsubUrl,
                "Open simulation", simLink == null ? appBaseUrl : simLink);
        return new Rendered(subject, plain, html);
    }

    private Rendered marketResearchStrategyCreated(Notification n, String unsubUrl, String appBaseUrl) {
        String strategyName = string(n, "strategyName", "new strategy");
        String viability = string(n, "viabilityScore", "");
        String strategyId = string(n, "strategyId", "");
        String subject = "QuorumTrading: research produced a new strategy — " + strategyName;
        String body = viability.isEmpty()
                ? "Your market research produced " + strategyName + "."
                : "Your market research produced " + strategyName + " (viability " + viability + ").";
        String strategyLink = strategyId.isEmpty()
                ? appBaseUrl + "/strategies"
                : appBaseUrl + "/strategies/" + strategyId;
        String plain = """
                %s

                %s

                Review the strategy and back-test it:
                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(), body, strategyLink, unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), body, null, unsubUrl,
                "Open strategy", strategyLink);
        return new Rendered(subject, plain, html);
    }

    private Rendered generic(Notification n, String unsubUrl, String appBaseUrl) {
        String subject = "QuorumTrading: " + n.getTitle();
        String body = n.getBody() == null ? n.getTitle() : n.getBody();
        String plain = """
                %s

                %s

                %s

                — QuorumTrading

                %s
                """.formatted(n.getTitle(), body, appBaseUrl, unsubscribeFooter(unsubUrl));
        String html = wrapHtml(n.getTitle(), body, null, unsubUrl,
                "Open QuorumTrading", appBaseUrl);
        return new Rendered(subject, plain, html);
    }

    // ---- Helpers ----

    private static String string(Notification n, String key, String fallback) {
        Object value = n.getPayload().get(key);
        return value == null ? fallback : value.toString();
    }

    private static String allocationLink(Notification n, String appBaseUrl) {
        Object planId = n.getPayload().get("planId");
        if (planId == null) return appBaseUrl + "/investment-plans";
        return appBaseUrl + "/investment-plans/" + planId;
    }

    private static String simulationLink(Notification n, String appBaseUrl) {
        Object simId = n.getPayload().get("simulationId");
        if (simId == null) return null;
        return appBaseUrl + "/simulations/" + simId;
    }

    private static String unsubscribeFooter(String unsubUrl) {
        return "Unsubscribe from this notification type: " + unsubUrl;
    }

    private static String wrapHtml(String headline, String body, String simulationLink,
                                   String unsubUrl, String ctaLabel, String ctaUrl) {
        String safeBody = body == null ? "" : escape(body);
        String simSnippet = simulationLink == null ? ""
                : "<p>Simulation: <a href=\"" + escape(simulationLink) + "\">"
                        + escape(simulationLink) + "</a></p>";
        return """
                <!doctype html>
                <html><body style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#222;max-width:560px;margin:0 auto;padding:24px;">
                  <h2 style="color:#0a7;">%s</h2>
                  <p>%s</p>
                  %s
                  <p style="margin-top:24px;">
                    <a href="%s" style="display:inline-block;padding:10px 16px;background:#0a7;color:#fff;border-radius:4px;text-decoration:none;">%s</a>
                  </p>
                  <hr style="margin-top:32px;border:0;border-top:1px solid #eee;">
                  <p style="font-size:12px;color:#666;">
                    <a href="%s" style="color:#666;">Unsubscribe from this notification type</a>
                  </p>
                </body></html>
                """.formatted(
                escape(headline),
                safeBody,
                simSnippet,
                escape(ctaUrl),
                escape(ctaLabel),
                escape(unsubUrl));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Visible for tests. */
    static boolean knows(NotificationType type) {
        return type != null;
    }
}
