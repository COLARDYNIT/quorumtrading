package com.quorumtrading.adapter.rest.exception;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.exception.AccountAccessDeniedException;
import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.exception.FreeModelRateLimitExceededException;
import com.quorumtrading.application.exception.InsufficientFundsException;
import com.quorumtrading.model.exception.StrategyValidationException;
import jakarta.annotation.Priority;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception mapper that converts all exceptions to RFC 9457 Problem Details format.
 * All responses are emitted with the {@code application/problem+json} media type and a
 * type URI under {@code https://api.quorumtrading.com/problems/}.
 */
@Provider
@Priority(1)
public class ProblemDetailsExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(ProblemDetailsExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Context
    HttpHeaders httpHeaders;

    @Override
    public Response toResponse(Exception exception) {
        String requestPath = uriInfo != null ? uriInfo.getPath() : "/unknown";

        // Handle JAX-RS NotFoundException (unknown routes/resources)
        if (exception instanceof jakarta.ws.rs.NotFoundException) {
            String userAgent = getHeader("User-Agent");
            String referer = getHeader("Referer");
            LOG.warnf("Unknown route requested - Path: %s, User-Agent: %s, Referer: %s",
                requestPath, userAgent, referer);
            return problemResponse(ProblemDetails.notFound(
                "The requested resource was not found.",
                requestPath
            ));
        }

        // Handle other JAX-RS WebApplicationExceptions (ForbiddenException, BadRequestException, etc.)
        if (exception instanceof jakarta.ws.rs.WebApplicationException webEx
                && !(exception instanceof jakarta.ws.rs.NotFoundException)) {
            return handleWebApplicationException(webEx, requestPath);
        }

        // Handle validation exceptions
        if (exception instanceof ConstraintViolationException) {
            return handleConstraintViolation((ConstraintViolationException) exception, requestPath);
        }

        // Handle our custom validation exception
        if (exception instanceof ValidationException) {
            return handleValidationException((ValidationException) exception, requestPath);
        }

        // Handle domain-level strategy validation failures (400 with per-error details)
        if (exception instanceof StrategyValidationException strategyValidation) {
            LOG.warnf("Strategy validation failed: %s - Path: %s", strategyValidation.getMessage(), requestPath);
            ProblemDetails problem = ProblemDetails.of(
                "validation-error",
                "Strategy Validation Failed",
                400,
                strategyValidation.getMessage(),
                requestPath
            );
            problem.setErrors(strategyValidation.getErrors().stream()
                .map(message -> new ProblemDetails.ValidationError("strategy", message, null))
                .collect(Collectors.toList()));
            return problemResponse(problem);
        }

        // Handle application-level EntityNotFoundException
        if (exception instanceof EntityNotFoundException) {
            LOG.warnf("Entity not found: %s - Path: %s", exception.getMessage(), requestPath);
            return problemResponse(ProblemDetails.notFound(exception.getMessage(), requestPath));
        }

        // Handle java.util.NoSuchElementException (thrown by application services for not-found entities)
        if (exception instanceof java.util.NoSuchElementException) {
            LOG.warnf("Entity not found: %s - Path: %s", exception.getMessage(), requestPath);
            return problemResponse(ProblemDetails.notFound(exception.getMessage(), requestPath));
        }

        // Handle FeatureDisabledException (403 Forbidden, naming the flag)
        //
        // Reached when a service guards itself rather than relying on FeatureFlagGateFilter — a
        // path the filter's prefix list does not cover. The message names the flag on purpose:
        // these gate product areas, not secrets, and "not available" with no further detail sends
        // the reader hunting through configuration.
        if (exception instanceof com.quorumtrading.application.exception.FeatureDisabledException featureDisabled) {
            LOG.warnf("Feature '%s' is disabled - Path: %s", featureDisabled.getFlagName(), requestPath);
            return problemResponse(ProblemDetails.of(
                "feature-disabled",
                "Feature not available",
                403,
                featureDisabled.getMessage(),
                requestPath
            ));
        }

        // Handle AccessDeniedException (generic 403 Forbidden)
        if (exception instanceof AccessDeniedException accessDenied) {
            LOG.warnf("Access denied: %s - Path: %s", accessDenied.getMessage(), requestPath);
            return problemResponse(ProblemDetails.of(
                "access-denied",
                "Access Denied",
                403,
                accessDenied.getMessage(),
                requestPath
            ));
        }

        // Handle SecurityException (403 Forbidden)
        //
        // InvestmentPlan, InvestmentAllocation, Notification and ApiToken enforce ownership by
        // throwing java.lang.SecurityException instead of AccessDeniedException. That was
        // unmapped, so a cross-user access attempt surfaced as a 500 — indistinguishable from a
        // real fault, and the message embeds a user UUID. Map it to the same 403 the other
        // convention produces, and do not echo the message.
        if (exception instanceof SecurityException) {
            LOG.warnf("Access denied (SecurityException): %s - Path: %s", exception.getMessage(), requestPath);
            return problemResponse(ProblemDetails.of(
                "access-denied",
                "Access Denied",
                403,
                "You do not have access to this resource.",
                requestPath
            ));
        }

        // Handle AccountAccessDeniedException (403 Forbidden)
        if (exception instanceof AccountAccessDeniedException accessDenied) {
            LOG.warnf("Account access denied: %s - Path: %s", accessDenied.getMessage(), requestPath);
            return problemResponse(ProblemDetails.of(
                "access-denied",
                "Access Denied",
                403,
                accessDenied.getMessage(),
                requestPath
            ));
        }

        // Handle BudgetDepletedException (402 Payment Required)
        if (exception instanceof BudgetDepletedException) {
            LOG.warnf("Budget depleted: %s - Path: %s", exception.getMessage(), requestPath);
            return problemResponse(ProblemDetails.budgetDepleted(
                "Your AI budget has been depleted. Please top up your budget to continue using AI features.",
                requestPath
            ));
        }

        // Handle DailySpendLimitExceededException (429 Too Many Requests)
        if (exception instanceof com.quorumtrading.application.exception.DailySpendLimitExceededException dailyEx) {
            LOG.warnf("Daily spend limit exceeded: %s - Path: %s", dailyEx.getMessage(), requestPath);
            return problemResponse(ProblemDetails.dailySpendLimitExceeded(
                "Your daily spend limit of $" + dailyEx.getDailyLimit()
                    + " has been reached (today's spend: $" + dailyEx.getTodaySpend()
                    + "). AI operations will resume tomorrow, or you can increase your daily limit at /settings/budget.",
                requestPath
            ));
        }

        // Handle FreeModelRateLimitExceededException (429 Too Many Requests)
        if (exception instanceof FreeModelRateLimitExceededException rateLimitEx) {
            LOG.warnf("Free model rate limit exceeded: %s - Path: %s", rateLimitEx.getMessage(), requestPath);
            return problemResponse(ProblemDetails.rateLimitExceeded(
                "Free model daily limit of " + rateLimitEx.getDailyLimit() + " calls reached. "
                    + "Try again tomorrow or switch to a paid model for unlimited usage.",
                requestPath
            ));
        }

        // Handle InsufficientFundsException (422 Unprocessable Entity)
        if (exception instanceof InsufficientFundsException insufficientFunds) {
            LOG.warnf("Insufficient funds: %s - Path: %s", insufficientFunds.getMessage(), requestPath);
            return problemResponse(ProblemDetails.of(
                "insufficient-funds",
                "Insufficient Funds",
                422,
                insufficientFunds.getMessage(),
                requestPath
            ));
        }

        // Handle REST-level not found
        if (exception instanceof NotFoundException notFound) {
            LOG.warnf("Resource not found: %s - Path: %s", notFound.getMessage(), requestPath);
            ProblemDetails problem = ProblemDetails.notFound(notFound.getMessage(), requestPath);
            problem.setStatus(notFound.getStatusCode());
            return problemResponse(problem);
        }

        // Handle conflict
        if (exception instanceof ConflictException conflict) {
            LOG.warnf("Conflict: %s - Path: %s", conflict.getMessage(), requestPath);
            ProblemDetails problem = ProblemDetails.conflict(conflict.getMessage(), requestPath);
            problem.setStatus(conflict.getStatusCode());
            return problemResponse(problem);
        }

        // Handle bad request
        if (exception instanceof BadRequestException badRequest) {
            LOG.warnf("Bad request: %s - Path: %s", badRequest.getMessage(), requestPath);
            return problemResponse(ProblemDetails.of(
                "bad-request",
                "Bad Request",
                400,
                badRequest.getMessage(),
                requestPath
            ));
        }

        // A rejected model pin is a validation error that knows the answer. The message already
        // names the model, the engine and the alternatives (MCP renders exactly that string); REST
        // repeats it in `detail` and adds the list as an RFC 7807 extension member so a UI can
        // render choices instead of parsing prose.
        if (exception instanceof com.quorumtrading.model.exception.UnservableModelPinException pinError) {
            LOG.warnf("Unservable model pin '%s' rejected for engine %s - Path: %s",
                    pinError.getPinnedId(), pinError.getEngine(), requestPath);
            ProblemDetails problem = ProblemDetails.of(
                    "unservable-model-pin",
                    "Validation Error",
                    400,
                    pinError.getMessage(),
                    requestPath);
            problem.setValidModels(pinError.getCandidates().stream()
                    .map(candidate -> new ProblemDetails.ValidModel(
                            candidate.id(),
                            candidate.tier() == null ? null : candidate.tier().name(),
                            candidate.bestContext() == null ? null : candidate.bestContext().name(),
                            candidate.reason()))
                    .toList());
            problem.setFullModelListing(pinError.getFullListingHint());
            return problemResponse(problem);
        }

        // Handle IllegalArgumentException as validation error (400)
        if (exception instanceof IllegalArgumentException) {
            LOG.warnf("Validation failed: %s - Path: %s", exception.getMessage(), requestPath);
            return problemResponse(ProblemDetails.of(
                "validation-error",
                "Validation Error",
                400,
                exception.getMessage(),
                requestPath
            ));
        }

        // Handle IllegalStateException as conflict (409)
        if (exception instanceof IllegalStateException) {
            LOG.warnf("Conflict: %s - Path: %s", exception.getMessage(), requestPath);
            return problemResponse(ProblemDetails.conflict(exception.getMessage(), requestPath));
        }

        // Handle unprocessable entity
        if (exception instanceof UnprocessableEntityException unprocessable) {
            LOG.warnf("Unprocessable entity: %s - Path: %s", unprocessable.getMessage(), requestPath);
            return problemResponse(ProblemDetails.unprocessableEntity(unprocessable.getMessage(), requestPath));
        }

        // Handle generic API exceptions
        if (exception instanceof ApiException apiEx) {
            LOG.errorf(apiEx, "API exception: %s - Path: %s", apiEx.getMessage(), requestPath);
            return problemResponse(ProblemDetails.of(
                "api-error",
                "API Error",
                apiEx.getStatusCode(),
                apiEx.getMessage(),
                requestPath
            ));
        }

        // Handle persistence/database errors we can map to a meaningful client error;
        // anything unrecognized (connection loss, deadlock, flush errors) falls through to the 500 branch.
        if (exception instanceof PersistenceException) {
            Response mapped = mapPersistenceException(exception, requestPath);
            if (mapped != null) {
                return mapped;
            }
        }

        // Handle unexpected errors
        LOG.errorf(exception, "Unexpected error occurred - Path: %s", requestPath);
        return problemResponse(ProblemDetails.internalError(
            "An unexpected error occurred. Please try again later.",
            requestPath
        ));
    }

    /**
     * Builds the RFC 9457 response: entity with Content-Type {@code application/problem+json}.
     */
    private Response problemResponse(ProblemDetails problem) {
        return Response.status(problem.getStatus())
            .entity(problem)
            .type(ProblemDetails.MEDIA_TYPE)
            .build();
    }

    private Response handleWebApplicationException(jakarta.ws.rs.WebApplicationException webEx, String requestPath) {
        int status = webEx.getResponse().getStatus();
        Response.Status statusEnum = Response.Status.fromStatusCode(status);
        String title = statusEnum != null ? statusEnum.getReasonPhrase() : "HTTP Error";

        String detail;
        if (status >= 500) {
            LOG.errorf(webEx, "Server error (%d): %s - Path: %s", status, webEx.getMessage(), requestPath);
            detail = "An unexpected error occurred. Please try again later.";
        } else {
            LOG.warnf("Client error (%d): %s - Path: %s", status, webEx.getMessage(), requestPath);
            detail = webEx.getMessage();
        }

        String slug = webEx instanceof jakarta.ws.rs.BadRequestException ? "bad-request" : "http-error";
        return problemResponse(ProblemDetails.of(slug, title, status, detail, requestPath));
    }

    /**
     * Maps recognized database constraint violations to client errors:
     * unique constraint → 409 Conflict, other recognized constraints → 400.
     * Returns {@code null} for anything unrecognized so the caller falls
     * through to the generic 500 branch (ERROR log with stack trace, sanitized message).
     */
    private Response mapPersistenceException(Exception exception, String requestPath) {
        Throwable cause = exception;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null) {
                if (msg.contains("violates unique constraint")) {
                    LOG.warnf("Unique constraint violation: %s - Path: %s", msg, requestPath);
                    return problemResponse(ProblemDetails.conflict(
                        "A record with this value already exists.", requestPath));
                }
                if (msg.contains("violates not-null constraint")) {
                    LOG.warnf("Not-null constraint violation: %s - Path: %s", msg, requestPath);
                    return problemResponse(ProblemDetails.of(
                        "database-error", "Database Error", 400,
                        "A required field is missing.", requestPath));
                }
                if (msg.contains("violates foreign key constraint")) {
                    LOG.warnf("Foreign key constraint violation: %s - Path: %s", msg, requestPath);
                    return problemResponse(ProblemDetails.of(
                        "database-error", "Database Error", 400,
                        "Referenced record not found.", requestPath));
                }
                if (msg.contains("violates check constraint")) {
                    LOG.warnf("Check constraint violation: %s - Path: %s", msg, requestPath);
                    return problemResponse(ProblemDetails.of(
                        "database-error", "Database Error", 400,
                        "A field value is invalid.", requestPath));
                }
            }
            cause = cause.getCause();
        }
        return null;
    }

    private Response handleConstraintViolation(ConstraintViolationException exception, String requestPath) {
        List<ProblemDetails.ValidationError> errors = exception.getConstraintViolations().stream()
            .map(this::toValidationError)
            .collect(Collectors.toList());

        LOG.warnf("Validation failed: %d violations - Path: %s", errors.size(), requestPath);

        return problemResponse(ProblemDetails.validationError(
            "Request validation failed",
            requestPath,
            errors
        ));
    }

    private Response handleValidationException(ValidationException exception, String requestPath) {
        LOG.warnf("Validation failed: %s - Path: %s", exception.getMessage(), requestPath);
        ProblemDetails problem = ProblemDetails.validationError(
            exception.getMessage(),
            requestPath,
            exception.getErrors()
        );
        problem.setStatus(exception.getStatusCode());
        return problemResponse(problem);
    }

    private ProblemDetails.ValidationError toValidationError(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        String message = violation.getMessage();
        Object rejectedValue = violation.getInvalidValue();
        return new ProblemDetails.ValidationError(field, message, rejectedValue);
    }

    private String getHeader(String headerName) {
        if (httpHeaders == null) {
            return null;
        }
        return httpHeaders.getHeaderString(headerName);
    }
}
