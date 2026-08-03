package com.quorumtrading.adapter.marketdata.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from Alpaca News API (GET /v1beta1/news).
 * Contains paginated list of news articles.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlpacaNewsResponse {

    @JsonProperty("news")
    private List<AlpacaNewsArticle> news;

    @JsonProperty("next_page_token")
    private String nextPageToken;

    // Getters
    public List<AlpacaNewsArticle> getNews() { return news; }
    public String getNextPageToken() { return nextPageToken; }

    // Setters (for Jackson deserialization)
    public void setNews(List<AlpacaNewsArticle> news) { this.news = news; }
    public void setNextPageToken(String nextPageToken) { this.nextPageToken = nextPageToken; }
}
