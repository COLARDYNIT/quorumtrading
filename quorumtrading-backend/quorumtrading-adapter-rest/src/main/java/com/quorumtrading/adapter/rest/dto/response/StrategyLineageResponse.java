package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Provenance of the strategy attached to a simulation: whether the
 * user authored it from scratch ("OWN") or forked it from a publicly
 * curated strategy ("PUBLIC"). A fork is created by an explicit
 * clone of a public strategy; the clone becomes private to the user
 * and tracks lineage via parentStrategyId.
 */
@Schema(description = "Strategy provenance for a simulation")
public record StrategyLineageResponse(
    @Schema(description = "Where the strategy originated. OWN = authored by the simulation owner; " +
            "PUBLIC = forked from a publicly curated strategy.",
            example = "OWN",
            enumeration = {"OWN", "PUBLIC"})
    String origin,

    @Schema(description = "ID of the strategy this one was forked from. Null when origin is OWN.",
            example = "660f9511-f39c-52e5-b827-557766551111",
            nullable = true)
    String parentStrategyId,

    @Schema(description = "Fork depth from the original strategy. 0 for an own/original strategy, " +
            "1 for a direct fork of a public strategy, etc.",
            example = "0")
    int generation
) {}
