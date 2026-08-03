package com.quorumtrading.adapter.marketdata.rss;

import java.util.List;

public class RSSFeedConfig {

    private final String name;
    private final List<String> feedUrls;

    public RSSFeedConfig(String name, List<String> feedUrls) {
        this.name = name;
        this.feedUrls = feedUrls;
    }

    public String getName() {
        return name;
    }

    public List<String> getFeedUrls() {
        return feedUrls;
    }
}
