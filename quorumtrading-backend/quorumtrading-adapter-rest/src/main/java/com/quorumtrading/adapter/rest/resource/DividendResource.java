package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.DividendInfoResponse;
import com.quorumtrading.adapter.rest.dto.response.DividendInfoResponse.DividendInfoItem;
import com.quorumtrading.application.usecase.GetDividendInfoUseCase;
import com.quorumtrading.application.usecase.query.GetDividendInfoQuery;
import com.quorumtrading.model.valueobject.DividendInfo;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/api/v1/dividends")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Dividends", description = "Dividend information for tickers")
@PermitAll
public class DividendResource {

    @Inject
    GetDividendInfoUseCase getDividendInfoUseCase;

    @GET
    @Path("/info")
    @Operation(summary = "Get dividend info for tickers",
        description = "Returns dividend information (yield, payment count) for a comma-separated list of ticker symbols.")
    @APIResponse(responseCode = "200", description = "Dividend info retrieved")
    @APIResponse(responseCode = "400", description = "No symbols provided")
    public Response getDividendInfo(
        @Parameter(description = "Comma-separated ticker symbols (e.g., AAPL,MSFT,BTC)")
        @QueryParam("symbols") String symbols
    ) {
        if (symbols == null || symbols.isBlank()) {
            throw new com.quorumtrading.adapter.rest.exception.BadRequestException(
                "symbols query parameter is required");
        }

        List<String> tickers = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        if (tickers.isEmpty()) {
            throw new com.quorumtrading.adapter.rest.exception.BadRequestException(
                "At least one ticker symbol is required");
        }

        Map<String, DividendInfo> result = getDividendInfoUseCase.execute(new GetDividendInfoQuery(tickers));

        List<DividendInfoItem> items = result.values().stream()
            .map(info -> new DividendInfoItem(
                info.ticker(),
                info.paysDividends(),
                info.annualYieldEstimate(),
                info.dividendCountLastYear(),
                info.assetType()
            ))
            .toList();

        return Response.ok(new DividendInfoResponse(items)).build();
    }
}
