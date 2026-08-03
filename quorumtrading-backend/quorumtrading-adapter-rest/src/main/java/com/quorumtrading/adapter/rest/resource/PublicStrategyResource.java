package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.StrategyResponse;
import com.quorumtrading.adapter.rest.mapper.StrategyMapper;
import com.quorumtrading.application.usecase.ListPublicStrategiesUseCase;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Path("/api/v1/strategies/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@PermitAll
@Tag(name = "Public Strategies", description = "Browse and use publicly available strategies")
public class PublicStrategyResource {

    @Inject
    ListPublicStrategiesUseCase listPublicStrategiesUseCase;

    @Inject
    StrategyMapper strategyMapper;

    @GET
    @Operation(summary = "List all public strategies", description = "Returns all publicly visible, active strategies.")
    @APIResponse(responseCode = "200", description = "Public strategies retrieved",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse[].class)))
    public Response listPublic() {
        List<Strategy> strategies = listPublicStrategiesUseCase.execute();
        List<StrategyResponse> response = strategyMapper.toResponseList(strategies);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a public strategy", description = "Returns a single public strategy by ID.")
    @APIResponse(responseCode = "200", description = "Public strategy retrieved",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse.class)))
    @APIResponse(responseCode = "404", description = "Strategy not found or not public")
    public Response getPublic(@PathParam("id") UUID id) {
        try {
            Strategy strategy = listPublicStrategiesUseCase.findPublicById(new StrategyId(id));
            return Response.ok(strategyMapper.toResponse(strategy)).build();
        } catch (NoSuchElementException e) {
            throw new NotFoundException("Strategy not found");
        }
    }
}
