package com.quorumtrading.adapter.rest.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 * Provides a standardized error response format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "RFC 9457 Problem Details for HTTP APIs - standardized error response")
public class ProblemDetails {

    /** RFC 9457 media type for problem detail responses. */
    public static final String MEDIA_TYPE = "application/problem+json";

    /** Base URI namespace for all problem type URIs. */
    public static final String TYPE_BASE = "https://api.quorumtrading.com/problems/";

    @Schema(description = "URI reference identifying the problem type", example = "https://api.quorumtrading.com/problems/validation-error")
    private String type;

    @Schema(description = "Short, human-readable summary", example = "Validation Failed")
    private String title;

    @Schema(description = "HTTP status code", example = "400")
    private Integer status;

    @Schema(description = "Human-readable explanation specific to this occurrence", example = "Version is required")
    private String detail;

    @Schema(description = "URI reference identifying the specific occurrence", example = "/api/v1/strategies")
    private String instance;

    @Schema(description = "Timestamp when the error occurred", example = "2025-01-20T10:30:00Z")
    private Instant timestamp;

    @Schema(description = "List of validation errors (present for 400 Bad Request responses)")
    private List<ValidationError> errors;

    @Schema(description = "URL to budget management page (present for 402 responses)", nullable = true)
    private String budgetUrl;

    @Schema(description = "Models the active LLM engine would actually run (present when a model pin "
            + "was rejected). A curated subset when the engine's catalogue is large.", nullable = true)
    private List<ValidModel> validModels;

    @Schema(description = "Where to find the complete model list when validModels is a subset", nullable = true)
    private String fullModelListing;

    public ProblemDetails() {
        this.timestamp = Instant.now();
    }

    public ProblemDetails(String type, String title, Integer status, String detail, String instance) {
        this();
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
    }

    /**
     * Generic factory: builds a problem with a type URI under the canonical
     * {@code https://api.quorumtrading.com/problems/} namespace.
     *
     * @param slug kebab-case problem slug appended to the /problems/ namespace
     */
    public static ProblemDetails of(String slug, String title, int status, String detail, String instance) {
        return new ProblemDetails(TYPE_BASE + slug, title, status, detail, instance);
    }

    public static ProblemDetails validationError(String detail, String instance, List<ValidationError> errors) {
        ProblemDetails problem = new ProblemDetails(
            "https://api.quorumtrading.com/problems/validation-error",
            "Validation Failed",
            400,
            detail,
            instance
        );
        problem.setErrors(errors);
        return problem;
    }

    public static ProblemDetails notFound(String detail, String instance) {
        return new ProblemDetails(
            "https://api.quorumtrading.com/problems/resource-not-found",
            "Resource Not Found",
            404,
            detail,
            instance
        );
    }

    public static ProblemDetails conflict(String detail, String instance) {
        return new ProblemDetails(
            "https://api.quorumtrading.com/problems/conflict",
            "Conflict",
            409,
            detail,
            instance
        );
    }

    public static ProblemDetails unprocessableEntity(String detail, String instance) {
        return new ProblemDetails(
            "https://api.quorumtrading.com/problems/unprocessable-entity",
            "Unprocessable Entity",
            422,
            detail,
            instance
        );
    }

    public static ProblemDetails internalError(String detail, String instance) {
        return new ProblemDetails(
            "https://api.quorumtrading.com/problems/internal-error",
            "Internal Server Error",
            500,
            detail,
            instance
        );
    }

    public static ProblemDetails budgetDepleted(String detail, String instance) {
        ProblemDetails problem = new ProblemDetails(
            "https://api.quorumtrading.com/problems/budget-depleted",
            "Budget Depleted",
            402,
            detail,
            instance
        );
        problem.setBudgetUrl("https://quorumtrading.com/settings/budget");
        return problem;
    }

    public static ProblemDetails dailySpendLimitExceeded(String detail, String instance) {
        ProblemDetails problem = new ProblemDetails(
            "https://api.quorumtrading.com/problems/daily-spend-limit-exceeded",
            "Daily Spend Limit Exceeded",
            429,
            detail,
            instance
        );
        problem.setBudgetUrl("https://quorumtrading.com/settings/budget");
        return problem;
    }

    public static ProblemDetails rateLimitExceeded(String detail, String instance) {
        return new ProblemDetails(
            "https://api.quorumtrading.com/problems/rate-limit-exceeded",
            "Rate Limit Exceeded",
            429,
            detail,
            instance
        );
    }

    /**
     * One model the active engine will serve, offered alongside a rejected pin.
     *
     * <p>An RFC 7807 extension member rather than prose in {@code detail}: the same information is
     * in the message for humans and MCP clients, but a UI should render choices, not parse
     * sentences.</p>
     */
    @Schema(description = "A model the active LLM engine can serve")
    public static class ValidModel {
        @Schema(description = "Model id exactly as the engine advertises it", example = "llama3.2")
        private String id;

        @Schema(description = "Curated tier, or null when the model is simply untested", example = "RECOMMENDED", nullable = true)
        private String tier;

        @Schema(description = "Trading context in which the tier was earned", example = "BACKTESTING", nullable = true)
        private String bestContext;

        @Schema(description = "Short justification for the tier", nullable = true)
        private String reason;

        public ValidModel() {}

        public ValidModel(String id, String tier, String bestContext, String reason) {
            this.id = id;
            this.tier = tier;
            this.bestContext = bestContext;
            this.reason = reason;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public String getBestContext() { return bestContext; }
        public void setBestContext(String bestContext) { this.bestContext = bestContext; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    @Schema(description = "Validation error details")
    public static class ValidationError {
        @Schema(description = "Field name that failed validation", example = "version")
        private String field;

        @Schema(description = "Validation error message", example = "Version is required")
        private String message;

        @Schema(description = "The value that was rejected", example = "")
        private Object rejectedValue;

        public ValidationError() {}

        public ValidationError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }

        // Getters and setters
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Object getRejectedValue() { return rejectedValue; }
        public void setRejectedValue(Object rejectedValue) { this.rejectedValue = rejectedValue; }
    }

    // Getters and setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public List<ValidationError> getErrors() { return errors; }
    public void setErrors(List<ValidationError> errors) { this.errors = errors; }
    public String getBudgetUrl() { return budgetUrl; }
    public void setBudgetUrl(String budgetUrl) { this.budgetUrl = budgetUrl; }
    public List<ValidModel> getValidModels() { return validModels; }
    public void setValidModels(List<ValidModel> validModels) { this.validModels = validModels; }
    public String getFullModelListing() { return fullModelListing; }
    public void setFullModelListing(String fullModelListing) { this.fullModelListing = fullModelListing; }
}
