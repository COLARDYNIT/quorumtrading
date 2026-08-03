/**
 * The `/v1/models` catalogue.
 *
 * This is a *static* list and answers without touching the CLI, because the consumer probes it with
 * a 3-second timeout (`LocalHttpLLMAdapter.isAvailable()` / `advertisedModels()`) and treats a
 * non-200 as "provider is down".
 *
 * Ordering is load-bearing. `LocalHttpLLMAdapter.firstAdvertisedModel()` takes `data[0]` whenever no
 * model is pinned, so the first entry becomes this engine's de-facto default. Sonnet is first
 * deliberately; opus is last so it can never become the accidental default for an unpinned agent.
 *
 * The ids are CLI *aliases* rather than resolved model names, because those are exactly the values
 * `claude --model` accepts. The model reported back on a completion is the resolved name the CLI
 * actually used (e.g. `claude-sonnet-5`), which is a different string on purpose — it is an
 * observation, not an echo.
 */

export function buildCatalog(models, createdAtSeconds = 1_700_000_000) {
  return {
    object: 'list',
    data: models.map((id) => ({
      id,
      object: 'model',
      created: createdAtSeconds,
      owned_by: 'anthropic-claude-cli',
    })),
  };
}
