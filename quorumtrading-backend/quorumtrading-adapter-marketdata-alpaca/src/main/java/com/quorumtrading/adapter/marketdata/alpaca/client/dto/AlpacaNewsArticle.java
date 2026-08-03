package com.quorumtrading.adapter.marketdata.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * News article from Alpaca News API (v1beta1).
 * Sourced from Benzinga, covers stocks and crypto back to 2015.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlpacaNewsArticle {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("headline")
    private String headline;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("author")
    private String author;

    @JsonProperty("source")
    private String source;

    @JsonProperty("url")
    private String url;

    @JsonProperty("symbols")
    private List<String> symbols;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    // Getters
    public Long getId() { return id; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public String getAuthor() { return author; }
    public String getSource() { return source; }
    public String getUrl() { return url; }
    public List<String> getSymbols() { return symbols; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    // Setters (for Jackson deserialization)
    public void setId(Long id) { this.id = id; }
    public void setHeadline(String headline) { this.headline = headline; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setAuthor(String author) { this.author = author; }
    public void setSource(String source) { this.source = source; }
    public void setUrl(String url) { this.url = url; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
