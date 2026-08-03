package com.quorumtrading.application.usecase.result;

import java.util.List;

/**
 * @param deletedStrategies how many strategies were removed — or would be, on a dry run
 * @param deletedResearch how many research records were removed — or would be, on a dry run
 * @param strategyNames the strategies concerned, named so a preview is actionable: a bare count
 *     does not tell you whether the batch you just wrote is the batch about to be deleted
 * @param researchNames the research records concerned
 * @param dryRun true when nothing was actually removed
 */
public record PruneUnusedResult(
    int deletedStrategies,
    int deletedResearch,
    List<String> strategyNames,
    List<String> researchNames,
    boolean dryRun
) {
    public PruneUnusedResult {
        strategyNames = strategyNames == null ? List.of() : List.copyOf(strategyNames);
        researchNames = researchNames == null ? List.of() : List.copyOf(researchNames);
    }

    /** Existing callers that only care about the counts. */
    public PruneUnusedResult(int deletedStrategies, int deletedResearch) {
        this(deletedStrategies, deletedResearch, List.of(), List.of(), false);
    }
}
