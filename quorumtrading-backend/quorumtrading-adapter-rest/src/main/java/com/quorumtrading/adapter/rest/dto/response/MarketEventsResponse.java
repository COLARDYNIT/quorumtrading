package com.quorumtrading.adapter.rest.dto.response;

import java.time.Instant;
import java.util.List;

public class MarketEventsResponse {

    public List<MarketEventResponse> events;
    public int total;
    public Instant from;
    public Instant to;
    public Integer limit;

    public MarketEventsResponse() {}

    public MarketEventsResponse(List<MarketEventResponse> events, int total, Instant from, Instant to, Integer limit) {
        this.events = events;
        this.total = total;
        this.from = from;
        this.to = to;
        this.limit = limit;
    }
}
