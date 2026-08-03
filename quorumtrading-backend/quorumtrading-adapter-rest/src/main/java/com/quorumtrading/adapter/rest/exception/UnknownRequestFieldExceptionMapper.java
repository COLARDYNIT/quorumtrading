package com.quorumtrading.adapter.rest.exception;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns "your request body has a field I do not know" into an answer the caller can act on.
 *
 * <p>Request bodies are parsed strictly — see {@code quarkus.jackson.fail-on-unknown-properties} —
 * because a silently discarded field reports success for something the server did not do:
 * {@code "expiresInDay": 30} was dropped and the token created with no expiry at all. Strictness
 * alone is not enough, though. The framework's own handling of that rejection is a bare 400 with
 * an empty body, which tells the caller only that something is wrong with a request they cannot
 * see a fault in. This names the offending field and lists the accepted ones.</p>
 *
 * <p>Registered for the specific exception type at {@code @Priority(1)} so it wins over both the
 * built-in mapper and the catch-all {@link ProblemDetailsExceptionMapper}, which would have
 * reported a user-fixable typo as an internal server error.</p>
 */
@Provider
@Priority(1)
public class UnknownRequestFieldExceptionMapper
        implements ExceptionMapper<UnrecognizedPropertyException> {

    private static final Logger LOG = Logger.getLogger(UnknownRequestFieldExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(UnrecognizedPropertyException exception) {
        String requestPath = uriInfo != null ? uriInfo.getPath() : "/unknown";
        String field = exception.getPropertyName();

        String accepted = exception.getKnownPropertyIds() == null ? ""
                : exception.getKnownPropertyIds().stream()
                    .map(String::valueOf)
                    .sorted()
                    .collect(Collectors.joining(", "));

        LOG.warnf("Unknown request body field '%s' - Path: %s", field, requestPath);

        ProblemDetails problem = ProblemDetails.of(
                "validation-error",
                "Unknown Request Field",
                400,
                "Unknown field '" + field + "' in the request body. The server does not read it, "
                    + "so accepting it would look like it applied."
                    + (accepted.isEmpty() ? "" : " Accepted fields: " + accepted + "."),
                requestPath
        );
        problem.setErrors(List.of(new ProblemDetails.ValidationError(field, "unknown field", null)));

        return Response.status(400)
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
