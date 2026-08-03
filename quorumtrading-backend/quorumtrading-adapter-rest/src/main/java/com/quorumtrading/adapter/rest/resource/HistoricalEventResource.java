package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.HistoricalEventResponse;
import com.quorumtrading.application.usecase.GetHistoricalEventUseCase;
import com.quorumtrading.application.usecase.ListHistoricalEventsUseCase;
import com.quorumtrading.application.usecase.query.GetHistoricalEventQuery;
import com.quorumtrading.application.usecase.query.ListHistoricalEventsQuery;
import com.quorumtrading.model.entity.HistoricalEvent;
import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Optional;

@Path("/api/v1/historical-events")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@PermitAll
public class HistoricalEventResource {

    private final ListHistoricalEventsUseCase listHistoricalEventsUseCase;
    private final GetHistoricalEventUseCase getHistoricalEventUseCase;

    public HistoricalEventResource(
            ListHistoricalEventsUseCase listHistoricalEventsUseCase,
            GetHistoricalEventUseCase getHistoricalEventUseCase) {
        this.listHistoricalEventsUseCase = listHistoricalEventsUseCase;
        this.getHistoricalEventUseCase = getHistoricalEventUseCase;
    }

    @GET
    public List<HistoricalEventResponse> list(
            @QueryParam("category") String category,
            @QueryParam("assetClass") String assetClass,
            @QueryParam("sentiment") String sentiment) {

        ListHistoricalEventsQuery query = new ListHistoricalEventsQuery(
                parseEnum(category, EventCategory.class),
                parseEnum(assetClass, AssetClass.class),
                parseEnum(sentiment, MarketSentiment.class));

        return listHistoricalEventsUseCase.execute(query).stream()
                .map(this::toResponse)
                .toList();
    }

    @GET
    @Path("/{slug}")
    public List<HistoricalEventResponse> getBySlug(@PathParam("slug") String slug) {
        return getHistoricalEventUseCase.execute(new GetHistoricalEventQuery(slug)).stream()
                .map(this::toResponse)
                .toList();
    }

    private HistoricalEventResponse toResponse(HistoricalEvent event) {
        return new HistoricalEventResponse(
                event.id(),
                event.slug(),
                event.name(),
                event.startDate().toString(),
                event.endDate().toString(),
                event.category().name(),
                event.assetClass().name(),
                event.sentiment().name(),
                event.description(),
                event.impactSummary(),
                event.suggestedAssets(),
                event.keywords());
    }

    private <T extends Enum<T>> Optional<T> parseEnum(String value, Class<T> enumType) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Enum.valueOf(enumType, value.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
