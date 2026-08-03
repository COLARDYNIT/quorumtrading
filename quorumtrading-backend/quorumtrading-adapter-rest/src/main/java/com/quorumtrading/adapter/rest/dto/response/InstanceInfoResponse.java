package com.quorumtrading.adapter.rest.dto.response;

import java.util.List;

/**
 * JSON view of the instance descriptor — which edition this deployment runs as and which broker
 * regions it offers. Anonymous by design: it carries deployment shape, never user data.
 *
 * @param edition {@code saas} (hosted multi-user) or {@code standalone} (single-user self-host)
 * @param brokers broker integrations this instance offers ({@code ALPACA}, {@code SAXO})
 * @param automaticInvestmentsEnabled whether the DCA scheduler is running on this instance. It is
 *     off by default everywhere — it moves real money — so an ACTIVE recurring investment plan
 *     will not fire until an operator enables it. The UI must not promise monthly execution
 *     while this is false; it is deployment shape, not a configuration value.
 */
public record InstanceInfoResponse(
        String edition,
        List<String> brokers,
        boolean automaticInvestmentsEnabled) {}
