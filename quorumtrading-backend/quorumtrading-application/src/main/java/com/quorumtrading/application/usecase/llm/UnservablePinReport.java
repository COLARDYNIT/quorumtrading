package com.quorumtrading.application.usecase.llm;

import com.quorumtrading.model.valueobject.ModelCandidate;

import java.util.List;

/**
 * What stopped working when the engine changed.
 *
 * <p>Switching engines is the realistic path to an unservable pin, and the normal journey of a
 * self-hoster who tries OpenRouter first and then moves to a local server: a preference or strategy
 * saved when it was perfectly valid becomes unservable, and nothing revalidates it. Under a
 * fail-hard policy the discovery would otherwise happen mid-backtest.</p>
 *
 * <p>Nothing here is silently repaired. Repairing would mean choosing a model on the user's behalf,
 * which is the substitution this whole change removes; the report gives them the chance to fix it
 * before the next run instead.</p>
 *
 * @param preferences stored user preferences the new engine will not serve
 * @param strategyPins saved strategy pins the new engine will not serve
 * @param candidates  models the new engine <em>will</em> serve, curated
 */
public record UnservablePinReport(
        List<UnservablePin> preferences,
        List<UnservablePin> strategyPins,
        List<ModelCandidate> candidates
) {

    public static final UnservablePinReport EMPTY = new UnservablePinReport(List.of(), List.of(), List.of());

    public UnservablePinReport {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        strategyPins = strategyPins == null ? List.of() : List.copyOf(strategyPins);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** Whether the switch left anything broken. */
    public boolean isClean() {
        return preferences.isEmpty() && strategyPins.isEmpty();
    }

    public int total() {
        return preferences.size() + strategyPins.size();
    }

    /**
     * One stored pin that the active engine cannot serve.
     *
     * @param where   human-readable location, e.g. {@code "preference BACKTESTING_TECHNICAL"} or
     *                {@code "strategy 'BTC Momentum' (llm_models.TECHNICAL)"}
     * @param modelId the id that will not run
     */
    public record UnservablePin(String where, String modelId) {}
}
