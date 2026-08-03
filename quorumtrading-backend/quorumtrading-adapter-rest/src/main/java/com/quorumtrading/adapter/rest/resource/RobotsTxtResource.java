package com.quorumtrading.adapter.rest.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * Serves robots.txt to prevent search engine crawling of the API domain.
 */
@Path("/robots.txt")
@ApplicationScoped
@PermitAll
public class RobotsTxtResource {

    private static final String ROBOTS_TXT = "User-agent: *\nDisallow: /\n";

    @GET
    @Produces("text/plain")
    public String robots() {
        return ROBOTS_TXT;
    }
}
