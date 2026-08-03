package com.quorumtrading.adapter.marketdata.finnhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FinnhubNewsResponse {

    @JsonProperty("category")
    private String category;

    @JsonProperty("datetime")
    private Long datetime;

    @JsonProperty("headline")
    private String headline;

    @JsonProperty("id")
    private Long id;

    @JsonProperty("image")
    private String image;

    @JsonProperty("related")
    private String related;

    @JsonProperty("source")
    private String source;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("url")
    private String url;

    // Getters and setters
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Long getDatetime() { return datetime; }
    public void setDatetime(Long datetime) { this.datetime = datetime; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getRelated() { return related; }
    public void setRelated(String related) { this.related = related; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
