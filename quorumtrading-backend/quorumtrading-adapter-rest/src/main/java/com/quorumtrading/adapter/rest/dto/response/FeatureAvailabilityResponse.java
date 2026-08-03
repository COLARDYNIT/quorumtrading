package com.quorumtrading.adapter.rest.dto.response;

import java.util.Map;

/**
 * Which flagged features are on for the caller making this request.
 *
 * <p>Evaluated per request rather than served as deployment shape, because a flag can be targeted
 * at individual people — two users of the same instance can legitimately get different answers.
 * That is why this is not folded into {@link InstanceInfoResponse}, which the frontend caches
 * forever.</p>
 *
 * @param features flag name (e.g. {@code "challenges"}) to whether it is on for this caller
 */
public record FeatureAvailabilityResponse(Map<String, Boolean> features) {}
