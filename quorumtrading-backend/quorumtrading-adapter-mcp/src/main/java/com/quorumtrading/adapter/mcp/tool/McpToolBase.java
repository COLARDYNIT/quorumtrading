package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.exception.AccountAccessDeniedException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract base class for MCP tool classes.
 * Provides shared error handling via {@link #execute(Supplier)} and JSON serialization.
 */
public abstract class McpToolBase {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Pattern matching value object wrappers like {@code StrategyId[uuid]} or {@code SimulationId[uuid]}.
     */
    private static final Pattern VALUE_OBJECT_WRAPPER = Pattern.compile("\\b(\\w+Id)\\[([^\\]]+)]");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    com.quorumtrading.application.usecase.EvaluateFeatureUseCase evaluateFeature;

    /**
     * Separate from the subclasses' own {@code userContext} field on purpose: this base class needs
     * the caller only to evaluate feature flags, and not every tool class resolves a user.
     */
    @Inject
    com.quorumtrading.adapter.mcp.auth.McpUserContext flagUserContext;

    @ConfigProperty(name = "quorumtrading.edition", defaultValue = "saas")
    String edition;

    /**
     * Executes a tool action with standardized error handling.
     * Catches common exceptions and returns proper {@link ToolResponse#error(String)} messages
     * instead of letting them propagate as opaque -32603 internal errors.
     */
    protected ToolResponse execute(Supplier<String> action) {
        try {
            return ToolResponse.success(action.get());
        } catch (SecurityException e) {
            return ToolResponse.error(authErrorMessage(e));
        } catch (DateTimeParseException e) {
            return ToolResponse.error("Invalid date format: '" + e.getParsedString()
                    + "'. Expected: YYYY-MM-DD or YYYY-MM-DDTHH:mm:ssZ");
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return ToolResponse.error(cleanErrorMessage(e.getMessage()));
        } catch (IllegalStateException e) {
            return ToolResponse.error(cleanErrorMessage(e.getMessage()));
        } catch (EntityNotFoundException e) {
            // A 404-class condition, not a server fault. These extend RuntimeException, so they
            // used to fall through to the generic branch below and come back as "Internal error:
            // Broker account not found: 00000000-…" — which sends a user looking for a backend
            // outage when the real problem is the id they typed.
            return ToolResponse.error(cleanErrorMessage(e.getMessage()));
        } catch (AccessDeniedException | AccountAccessDeniedException e) {
            // Likewise a decision the domain made deliberately, not a crash.
            return ToolResponse.error(cleanErrorMessage(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected MCP tool error", e);
            return ToolResponse.error("Internal error: " + cleanErrorMessage(e.getMessage()));
        } catch (Error e) {
            // Catch infrastructure errors (e.g. ServiceConfigurationError) that bypass Exception handlers.
            // Without this, they propagate raw to the MCP framework as opaque errors.
            logger.error("MCP tool infrastructure error", e);
            return ToolResponse.error("Infrastructure error: " + cleanErrorMessage(e.getMessage()));
        }
    }

    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize MCP tool response", e);
            return "{\"error\": \"Serialization failed\"}";
        }
    }

    /**
     * Lenient ISO-8601 formatter that handles optional seconds and fractional seconds.
     * Accepts: {@code 2026-02-26T00:00Z}, {@code 2026-02-26T00:00:00Z},
     * {@code 2026-02-26T00:00:00.000Z}, {@code 2026-02-26T00:00:00+01:00}.
     */
    private static final DateTimeFormatter LENIENT_ISO = new DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral('T')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .appendOffsetId()
        .toFormatter();

    /**
     * Parses a date string leniently. Accepts plain dates (YYYY-MM-DD), full ISO-8601,
     * and common LLM variations like missing seconds (YYYY-MM-DDTHH:mmZ).
     * Plain dates are interpreted as midnight UTC.
     */
    protected Instant parseDate(String dateStr) {
        if (!dateStr.contains("T")) {
            return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(dateStr, LENIENT_ISO).toInstant();
        }
    }

    /**
     * Parse the END of an inclusive date range.
     *
     * <p>A plain date means the whole of that day. {@link #parseDate} resolves it to
     * {@code T00:00:00Z}, which as a range end excludes the day the caller named: daily bars
     * are stamped at the session open (04:00Z for US equities), so "to 2024-12-31" returned
     * nothing for the 31st. Meanwhile {@code create_simulation} treats the same plain date as
     * inclusive, so the two tools disagreed about what one date string means.</p>
     *
     * <p>An explicit timestamp is honoured exactly as given.</p>
     */
    protected Instant parseEndDate(String dateStr) {
        if (!dateStr.contains("T")) {
            // Floored to MICROseconds, not nanoseconds. price_bars.timestamp is a Postgres
            // timestamptz, whose precision is microseconds, so a nanosecond-precision bound is
            // rounded — and .999999999 rounds UP:
            //
            //   SELECT '2026-07-10T23:59:59.999999999Z'::timestamptz;  ->  2026-07-11 00:00:00+00
            //
            // which turns "<= end of the 10th" into "<= midnight on the 11th" and pulls in the next
            // day's bar. Measured: BTC over 2026-07-01..07-10 reported the 11th's close and +6.36%
            // where the truth was +6.95%. US equity bars are stamped at the 04:00Z session open so
            // nothing sits on the rounded boundary and they were unaffected; crypto bars are stamped
            // at 00:00Z, exactly where it lands. A microsecond bound cannot round across a bar.
            return LocalDate.parse(dateStr)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .minus(1, ChronoUnit.MICROS);
        }
        return parseDate(dateStr);
    }

    /**
     * Parses an enum value with a user-friendly error listing valid values.
     */
    protected <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String paramName) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.stream(enumClass.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Invalid " + paramName + " '" + value + "'. Valid values: " + validValues);
        }
    }

    /**
     * Resolves a caller-supplied {@code limit} into a page size every list tool can trust.
     *
     * <p>Delegates to {@link #parseLimit(Optional, int, int, String)} naming the parameter
     * {@code limit}, which is what all but one list tool call it.</p>
     */
    protected int parseLimit(Optional<Integer> limit, int defaultLimit, int maxLimit) {
        return parseLimit(limit, defaultLimit, maxLimit, "limit");
    }

    /**
     * Resolves a caller-supplied row bound into a validated page size in {@code [1, maxLimit]}.
     *
     * <p>Absent means the tool's documented default. Above the documented max clamps down to it,
     * so a caller asking for more than the tool promises gets the promise rather than an
     * unbounded response.</p>
     *
     * <p><b>Zero and negatives are rejected, not honoured.</b> The alternative — returning zero
     * rows for {@code limit=0} — was what most list tools happened to do, and it is the worse
     * choice: an empty array is the same answer the tool gives when the account genuinely has
     * nothing, so a caller that computed its bound wrong concludes "no data" instead of "bad
     * request". Nobody deliberately asks a list tool for zero rows, so treating it as a mistake
     * and saying so loses no capability and removes an ambiguous answer. Rejecting also makes the
     * whole invalid range one contiguous interval with one message, rather than a negative branch
     * that errors and a zero branch that silently lies.</p>
     *
     * @throws IllegalArgumentException if the requested bound is below 1 — {@link #execute} turns
     *         this into a tool error naming the parameter and the bound
     */
    protected int parseLimit(Optional<Integer> limit, int defaultLimit, int maxLimit, String paramName) {
        int requested = limit.orElse(defaultLimit);
        if (requested < 1) {
            throw new IllegalArgumentException(
                    paramName + " must be between 1 and " + maxLimit + " (got " + requested + ")");
        }
        return Math.min(requested, maxLimit);
    }

    /**
     * One page of an over-fetched result set: the rows to return, and whether more exist.
     *
     * <p>{@code hasMore} is why list tools fetch {@code limit + 1} rows — the extra row is the
     * evidence, and it is never returned to the caller.</p>
     */
    protected record Page<T>(List<T> items, boolean hasMore) {
    }

    /**
     * Trims an over-fetched result set to {@code effectiveLimit} rows.
     *
     * <p>Exists so no tool computes the page boundary itself. {@code List#subList} with an
     * unvalidated bound raised {@code fromIndex(0) > toIndex(-3)} at the caller — a
     * {@code java.util} internal that names no parameter and offers no guidance. This method
     * re-checks the bound even though {@link #parseLimit} already did, because the guarantee has
     * to hold at the point of truncation, not merely upstream of it.</p>
     *
     * @param effectiveLimit a bound already through {@link #parseLimit}; must be at least 1
     */
    protected <T> Page<T> paginate(List<T> fetched, int effectiveLimit) {
        if (effectiveLimit < 1) {
            throw new IllegalArgumentException(
                    "limit must be at least 1 (got " + effectiveLimit + ")");
        }
        if (fetched.size() <= effectiveLimit) {
            return new Page<>(fetched, false);
        }
        return new Page<>(fetched.stream().limit(effectiveLimit).toList(), true);
    }

    /**
     * Parses a numeric string into BigDecimal with a user-friendly error.
     */
    protected BigDecimal parseBigDecimal(String value, String paramName) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid number for '" + paramName + "': '" + value
                            + "'. Expected a numeric value (e.g., 10000 or 10000.50)");
        }
    }

    /**
     * Strips value object wrappers (e.g. {@code StrategyId[uuid]} → {@code uuid}) from error messages
     * to make them more readable for LLM consumers.
     */
    protected String cleanErrorMessage(String message) {
        if (message == null) {
            return "Unknown error (no message)";
        }
        return VALUE_OBJECT_WRAPPER.matcher(message).replaceAll("$2");
    }

    /**
     * Whether a flagged feature is on for whoever is making this call.
     *
     * <p>Resolves the caller from their API token so that a flag targeted at one person behaves
     * over MCP exactly as it does in the web app. A call with no usable token evaluates as
     * anonymous rather than failing — the refusal a disabled feature deserves is
     * {@link #featureDisabled(Feature)}, not an authentication error.</p>
     */
    protected boolean isFeatureEnabled(Feature feature) {
        UserId userId;
        try {
            userId = flagUserContext.getUserId();
        } catch (RuntimeException e) {
            logger.debug("No MCP caller resolved for feature flag evaluation; evaluating anonymously", e);
            userId = null;
        }
        return evaluateFeature.isEnabledForUser(feature, userId);
    }

    /**
     * Refusal for a feature whose flag is switched off.
     *
     * <p>Names the flag deliberately. These gate whole product areas rather than anything secret,
     * and an agent told only "not available" will retry, rephrase, or invent an explanation for the
     * user; told which toggle to flip, it can say so and stop.</p>
     */
    protected ToolResponse featureDisabled(Feature feature) {
        return ToolResponse.error(
            "The '" + feature.flagName() + "' feature is switched off on this instance, so "
                + feature.description().toLowerCase(java.util.Locale.ROOT) + " is unavailable."
                + "\n\nAn administrator can switch on the '" + feature.flagName() + "' feature flag"
                + " under Admin -> Feature flags in the web app, or with"
                + " PUT /api/v1/admin/feature-flags/" + feature.flagName() + "."
                + "\n\nOn a self-hosted instance you are that administrator — the local owner is"
                + " provisioned with the admin role at boot.");
    }

    protected String authErrorMessage(SecurityException e) {
        if ("standalone".equalsIgnoreCase(edition)) {
            return "Authentication required: " + e.getMessage()
                    + "\n\nThis self-hosted instance provisioned its MCP token at boot. To fix your client:"
                    + "\n1. Read the current token from the boot banner: docker compose -f docker-compose.local.yml logs backend | grep -A10 \"MCP ready\""
                    + "\n2. Re-add the server: claude mcp add --transport http quorumtrading-local http://localhost:8085/mcp --header \"Authorization: Bearer <token-from-banner>\""
                    + "\n\nNote: wiping the data volume (docker compose down -v) generates a NEW token — stale client configs are the usual cause of this error.";
        }
        return "Authentication required: " + e.getMessage()
                + "\n\nThis tool requires an API token. To set one up:"
                + "\n1. Go to https://quorumtrading.com/settings/api-tokens"
                + "\n2. Click 'Generate Token' and copy the token (it is only shown once)"
                + "\n3. Configure your MCP client with: claude mcp add --transport http quorumtrading https://api.quorumtrading.com/mcp --header \"Authorization: Bearer <your-token>\"";
    }
}
