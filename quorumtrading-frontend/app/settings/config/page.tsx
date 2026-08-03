'use client';

import { useEffect, useMemo, useState } from 'react';
import styled, { css } from 'styled-components';
import toast from 'react-hot-toast';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel, KpiStrip, type KpiItem } from '@/app/components/shared/v2';
import {
  useAvailableModels,
  useModelPreferences,
  useUpdateModelPreferences,
  useFallbackConfig,
  useEffectiveModels,
  useModelReliability,
  useLlmProviderStatus,
  LLM_SETUP_URL,
} from '@/app/lib/api/hooks';
import {
  TRADING_CONTEXTS,
  effectiveModelFor,
  splitModelId,
  describeFallbackKind,
  TIER_ORDER,
  TIER_LABELS,
  getAgentRolesForContext,
  type TradingContext,
  type LLMModel,
  type ModelTier,
  type ModelReliabilityEntry,
} from '@/app/lib/types/config';

function formatCost(costPerMillion: number): string {
  if (costPerMillion === 0) return 'free';
  if (costPerMillion < 0.01) return '<$0.01/M';
  return `$${costPerMillion.toFixed(2)}/M`;
}

function groupModelsByTier(models: LLMModel[]): Map<ModelTier, LLMModel[]> {
  const grouped = new Map<ModelTier, LLMModel[]>();
  for (const tier of TIER_ORDER) {
    const tierModels = models.filter((m) => m.tier === tier);
    if (tierModels.length > 0) grouped.set(tier, tierModels);
  }
  return grouped;
}

function getReliabilityTag(
  entry: ModelReliabilityEntry | undefined,
): { label: string; variant: 'positive' | 'amber' | 'negative' } | null {
  if (!entry) return null;
  const total = entry.successCount + entry.failureCount;
  if (total < 10) return null;

  if (entry.failureRate < 0.02) {
    return { label: `99%+ reliable (${total} calls)`, variant: 'positive' };
  }
  if (entry.failureRate < 0.1) {
    return { label: `${Math.round((1 - entry.failureRate) * 100)}%+ reliable (${total} calls)`, variant: 'amber' };
  }
  return { label: `${Math.round(entry.failureRate * 100)}% parse failures (${total} calls)`, variant: 'negative' };
}

interface ModelSelectorProps {
  context: TradingContext;
  selectedModelId: string | null;
  onSelect: (modelId: string | null) => void;
  reliabilityMap?: Map<string, ModelReliabilityEntry>;
}

function ModelSelector({ context, selectedModelId, onSelect, reliabilityMap }: ModelSelectorProps) {
  const { data: models, isLoading, error } = useAvailableModels(context);
  const selectedModel = models?.find((m) => m.id === selectedModelId);
  const tierGroups = models ? groupModelsByTier(models) : new Map<ModelTier, LLMModel[]>();
  const hasTiers = tierGroups.size > 0;
  const reliabilityEntry = selectedModelId ? reliabilityMap?.get(selectedModelId) : undefined;
  const reliabilityTag = getReliabilityTag(reliabilityEntry);

  return (
    <SelectorWrap>
      <StyledSelect
        value={selectedModelId || ''}
        onChange={(e) => onSelect(e.target.value || null)}
        disabled={isLoading}
      >
        <option value="">— Select a model —</option>
        {hasTiers
          ? Array.from(tierGroups.entries()).map(([tier, tierModels]) => (
              <optgroup key={tier} label={TIER_LABELS[tier]}>
                {tierModels.map((model) => (
                  <option key={model.id} value={model.id}>
                    {model.name} ({formatCost(model.promptCostPerMillion)})
                  </option>
                ))}
              </optgroup>
            ))
          : models?.map((model) => (
              <option key={model.id} value={model.id}>
                {model.name} ({formatCost(model.promptCostPerMillion)})
              </option>
            ))}
      </StyledSelect>
      {selectedModel && (
        <ModelTags>
          <ModelTag>in: {formatCost(selectedModel.promptCostPerMillion)}</ModelTag>
          <ModelTag>out: {formatCost(selectedModel.completionCostPerMillion)}</ModelTag>
          {selectedModel.isFree && <ModelTag $variant="positive">free</ModelTag>}
          {selectedModel.supportsWebSearch && <ModelTag $variant="info">web search</ModelTag>}
          {selectedModel.contextLength > 0 && (
            <ModelTag>{Math.round(selectedModel.contextLength / 1000)}k ctx</ModelTag>
          )}
          {reliabilityTag && <ModelTag $variant={reliabilityTag.variant}>{reliabilityTag.label}</ModelTag>}
        </ModelTags>
      )}
      {selectedModel?.tier === 'AVOID' && selectedModel.tierReason && (
        <TierBanner $variant="negative">
          <span aria-hidden="true">!</span>
          <span>{selectedModel.tierReason}</span>
        </TierBanner>
      )}
      {selectedModel?.tier === 'EXPERIMENTAL' && selectedModel.tierReason && (
        <TierBanner $variant="amber">
          <span aria-hidden="true">i</span>
          <span>{selectedModel.tierReason}</span>
        </TierBanner>
      )}
      {error && (
        <ModelTags>
          <ModelTag $variant="amber">failed to load models</ModelTag>
        </ModelTags>
      )}
    </SelectorWrap>
  );
}

interface AgentModelSelectorProps {
  context: TradingContext;
  selectedModelId: string | null;
  onSelect: (modelId: string | null) => void;
}

function AgentModelSelector({ context, selectedModelId, onSelect }: AgentModelSelectorProps) {
  const { data: models, isLoading } = useAvailableModels(context);
  const tierGroups = models ? groupModelsByTier(models) : new Map<ModelTier, LLMModel[]>();
  const hasTiers = tierGroups.size > 0;

  return (
    <AgentSelect
      value={selectedModelId || ''}
      onChange={(e) => onSelect(e.target.value || null)}
      disabled={isLoading}
    >
      <option value="">Use context default</option>
      {hasTiers
        ? Array.from(tierGroups.entries()).map(([tier, tierModels]) => (
            <optgroup key={tier} label={TIER_LABELS[tier]}>
              {tierModels.map((model) => (
                <option key={model.id} value={model.id}>
                  {model.name} ({formatCost(model.promptCostPerMillion)})
                </option>
              ))}
            </optgroup>
          ))
        : models?.map((model) => (
            <option key={model.id} value={model.id}>
              {model.name} ({formatCost(model.promptCostPerMillion)})
            </option>
          ))}
    </AgentSelect>
  );
}

export default function ConfigPage() {
  const { data: preferences, isLoading: prefsLoading, error: prefsError } = useModelPreferences();
  const { data: fallbackConfig } = useFallbackConfig();
  // The backend computes which model each context will actually run; this page renders it and
  // computes nothing of its own, so it cannot drift away from what the engine does.
  const { data: effectiveModels } = useEffectiveModels();
  const effectiveByContext = useMemo(
    () =>
      Object.fromEntries((effectiveModels?.models ?? []).map((m) => [m.context, m])) as Record<
        string,
        (typeof effectiveModels extends undefined ? never : NonNullable<typeof effectiveModels>['models'][number]) | undefined
      >,
    [effectiveModels]
  );
  const { data: reliabilityData } = useModelReliability();
  // Self-host edition: when the local CLI engine is active, the OpenRouter model preferences on
  // this page have no effect — surface that instead of letting the page mislead.
  const { data: llmProviderStatus } = useLlmProviderStatus();
  const localEngineActive = llmProviderStatus?.selected === 'LOCAL';
  const updatePreferences = useUpdateModelPreferences();

  const reliabilityMap = useMemo(() => {
    if (!reliabilityData?.entries) return new Map<string, ModelReliabilityEntry>();
    return new Map(reliabilityData.entries.map((e) => [e.modelId, e]));
  }, [reliabilityData]);

  const [localPrefs, setLocalPrefs] = useState<Record<string, string | null>>({});
  const [localAgentOverrides, setLocalAgentOverrides] = useState<Record<string, Record<string, string | null>>>({});
  const [expandedContexts, setExpandedContexts] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (preferences) {
      setLocalPrefs({
        research: preferences.research,
        backtesting: preferences.backtesting,
        paperTrading: preferences.paperTrading,
        liveTrading: preferences.liveTrading,
        chat: preferences.chat,
      });
      setLocalAgentOverrides(preferences.agentOverrides ?? {});
      if (preferences.agentOverrides) {
        const expanded = new Set<string>();
        for (const ctx of Object.keys(preferences.agentOverrides)) {
          const overrides = preferences.agentOverrides[ctx];
          if (overrides && Object.values(overrides).some((v) => v != null)) {
            expanded.add(ctx);
          }
        }
        setExpandedContexts(expanded);
      }
    }
  }, [preferences]);

  const handleSelect = (fieldName: string, modelId: string | null) => {
    setLocalPrefs((prev) => ({ ...prev, [fieldName]: modelId }));
  };

  const handleAgentOverride = (contextKey: string, agentRole: string, modelId: string | null) => {
    setLocalAgentOverrides((prev) => {
      const contextOverrides = { ...(prev[contextKey] ?? {}) };
      if (modelId) {
        contextOverrides[agentRole] = modelId;
      } else {
        delete contextOverrides[agentRole];
      }
      if (Object.keys(contextOverrides).length === 0) {
        const next = { ...prev };
        delete next[contextKey];
        return next;
      }
      return { ...prev, [contextKey]: contextOverrides };
    });
  };

  const toggleAgentOverrides = (contextKey: string) => {
    setExpandedContexts((prev) => {
      const next = new Set(prev);
      if (next.has(contextKey)) next.delete(contextKey);
      else next.add(contextKey);
      return next;
    });
  };

  const handleSave = async () => {
    try {
      const agentOverridesToSend: Record<string, Record<string, string | null>> = {};
      const origOverrides = preferences?.agentOverrides ?? {};
      for (const [ctx, agents] of Object.entries(localAgentOverrides)) {
        agentOverridesToSend[ctx] = { ...agents };
      }
      for (const [ctx, agents] of Object.entries(origOverrides)) {
        if (agents) {
          for (const role of Object.keys(agents)) {
            if (!localAgentOverrides[ctx]?.[role]) {
              if (!agentOverridesToSend[ctx]) agentOverridesToSend[ctx] = {};
              agentOverridesToSend[ctx][role] = null;
            }
          }
        }
      }
      await updatePreferences.mutateAsync({
        research: localPrefs.research,
        backtesting: localPrefs.backtesting,
        paperTrading: localPrefs.paperTrading,
        liveTrading: localPrefs.liveTrading,
        chat: localPrefs.chat,
        agentOverrides: Object.keys(agentOverridesToSend).length > 0 ? agentOverridesToSend : undefined,
      });
      toast.success('Preferences saved');
    } catch (error) {
      console.error('Failed to save preferences:', error);
    }
  };

  const handleReset = () => {
    if (preferences) {
      setLocalPrefs({
        research: preferences.research,
        backtesting: preferences.backtesting,
        paperTrading: preferences.paperTrading,
        liveTrading: preferences.liveTrading,
        chat: preferences.chat,
      });
      setLocalAgentOverrides(preferences.agentOverrides ?? {});
    }
  };

  const hasContextChanges =
    preferences &&
    (localPrefs.research !== preferences.research ||
      localPrefs.backtesting !== preferences.backtesting ||
      localPrefs.paperTrading !== preferences.paperTrading ||
      localPrefs.liveTrading !== preferences.liveTrading ||
      localPrefs.chat !== preferences.chat);

  const hasAgentOverrideChanges =
    preferences && JSON.stringify(localAgentOverrides) !== JSON.stringify(preferences.agentOverrides ?? {});

  const hasChanges = hasContextChanges || hasAgentOverrideChanges;

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>LLM configuration</H1>
            <Desc>
              Pick which model the council uses for each context. The system falls through a shared
              chain on failures; failed models are temporarily blacklisted with exponential backoff
              (30 min → 12 h).
            </Desc>
          </HeaderLeft>
          <HeaderRight>
            <GhostBtn type="button" onClick={handleReset} disabled={!hasChanges || updatePreferences.isPending}>
              Reset
            </GhostBtn>
            <PrimaryBtn type="button" onClick={handleSave} disabled={!hasChanges || updatePreferences.isPending}>
              {updatePreferences.isPending ? 'Saving…' : 'Save preferences'}
            </PrimaryBtn>
          </HeaderRight>
        </PageHeader>

        {localEngineActive && (
          <LocalEngineBanner role="status">
            <BannerBadge>local engine active</BannerBadge>
            <BannerText>
              <strong>Local (claude CLI)</strong> is the active LLM engine — agents run through CLI
              tools on this machine, so the OpenRouter model preferences below have{' '}
              <strong>no effect</strong> right now. They apply again when OpenRouter is the active
              provider.
            </BannerText>
            <BannerLink href={LLM_SETUP_URL}>Change provider →</BannerLink>
          </LocalEngineBanner>
        )}

        {prefsError && (
          <Panel title="Preferences" noBodyPadding>
            <EmptyState>
              <EmptyIcon>!</EmptyIcon>
              <EmptyTitle>Couldn&apos;t load preferences.</EmptyTitle>
              <EmptyBody>{(prefsError as Error).message}</EmptyBody>
            </EmptyState>
          </Panel>
        )}

        {!prefsError && prefsLoading && (
          <Panel title="Preferences" noBodyPadding>
            <SkeletonBlock>
              {Array.from({ length: 4 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="40%" $h="13px" />
                  <SkelBar $w="60%" $h="32px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          </Panel>
        )}

        {!prefsError && !prefsLoading && preferences && (
          <PrefsSection $inactive={localEngineActive}>
            {fallbackConfig && (() => {
              const backtestingDefault = effectiveModelFor(effectiveModels, 'BACKTESTING') ?? '—';
              const { vendor: primaryVendor, name: primaryName } = splitModelId(backtestingDefault);
              return (
                <KpiStrip
                  items={[
                    {
                      label: 'Default model',
                      value: <KpiMono>{primaryName}</KpiMono>,
                      delta: primaryVendor ?? fallbackConfig.engine ?? '—',
                    },
                    {
                      label: 'Fallback depth',
                      value: <KpiMono>{fallbackConfig.fallbackChain.length}</KpiMono>,
                      delta: describeFallbackKind(fallbackConfig.kind),
                    },
                  ] as KpiItem[]}
                />
              );
            })()}

            <Panel
              title="Per-context preferences"
              count={TRADING_CONTEXTS.length}
              headerActions={
                <HeaderNote>
                  selected model wins over the system default · agent overrides win over context
                </HeaderNote>
              }
              noBodyPadding
            >
              <ContextRows>
                {TRADING_CONTEXTS.map((ctx) => {
                  const isExpanded = expandedContexts.has(ctx.key);
                  const agentRoles = getAgentRolesForContext(ctx.key);
                  const hasAgentRoles = agentRoles.length > 0;
                  const agentCount = localAgentOverrides[ctx.key]
                    ? Object.values(localAgentOverrides[ctx.key]).filter(Boolean).length
                    : 0;
                  const selected = localPrefs[ctx.fieldName] as string | null;

                  return (
                    <ContextRow key={ctx.key}>
                      <ContextInfo>
                        <ContextLabel>{ctx.label}</ContextLabel>
                        <ContextDesc>{ctx.description}</ContextDesc>
                        {!selected && (
                          <DefaultNote>
                            {/* Fetched, never restated: the hardcoded constant this replaced had
                                drifted into advertising a model the backend had already retired. */}
                            System default:{' '}
                            <Mono>{effectiveByContext[ctx.key]?.effective ?? '—'}</Mono>
                          </DefaultNote>
                        )}
                      </ContextInfo>
                      <ContextControls>
                        <ModelSelector
                          context={ctx.key}
                          selectedModelId={selected}
                          onSelect={(modelId) => handleSelect(ctx.fieldName, modelId)}
                          reliabilityMap={reliabilityMap}
                        />
                        {hasAgentRoles && (
                          <>
                            <OverridesToggle type="button" onClick={() => toggleAgentOverrides(ctx.key)}>
                              {isExpanded ? '[−]' : '[+]'} Per-agent overrides
                              {agentCount > 0 && ` (${agentCount} set)`}
                            </OverridesToggle>
                            {isExpanded && (
                              <OverridesGrid>
                                {agentRoles.map((agent) => (
                                  <OverrideRow key={agent.key}>
                                    <AgentLabel>{agent.label}</AgentLabel>
                                    <AgentModelSelector
                                      context={ctx.key}
                                      selectedModelId={localAgentOverrides[ctx.key]?.[agent.key] || null}
                                      onSelect={(modelId) => handleAgentOverride(ctx.key, agent.key, modelId)}
                                    />
                                  </OverrideRow>
                                ))}
                              </OverridesGrid>
                            )}
                          </>
                        )}
                      </ContextControls>
                    </ContextRow>
                  );
                })}
              </ContextRows>
            </Panel>

            {/* A chain is only shown when the ACTIVE engine actually has one. Rendering
                OpenRouter's models on a local engine advertised a fallback that could never
                happen. */}
            {fallbackConfig && fallbackConfig.fallbackChain.length === 0 && (
              <Panel title="Fallback" headerActions={<HeaderNote>read-only</HeaderNote>}>
                <DefaultNote>{fallbackConfig.description}</DefaultNote>
              </Panel>
            )}

            {fallbackConfig && fallbackConfig.fallbackChain.length > 0 && (
              <Panel
                title={fallbackConfig.kind === 'TOOL_CHAIN' ? 'Fallback tool chain' : 'Fallback chain'}
                count={fallbackConfig.fallbackChain.length}
                headerActions={
                  <HeaderNote>
                    read-only ·{' '}
                    {fallbackConfig.kind === 'TOOL_CHAIN'
                      ? 'installed CLI tools, tried in order'
                      : 'used when the selected model fails'}
                  </HeaderNote>
                }
                noBodyPadding
              >
                <FallbackList>
                  <FallbackHeaderBar>
                    <span />
                    <span />
                    <span>{fallbackConfig.kind === 'TOOL_CHAIN' ? 'Tool' : 'Model'}</span>
                    <RightAligned>Order</RightAligned>
                  </FallbackHeaderBar>
                  {fallbackConfig.fallbackChain.map((model, idx) => {
                    const [vendor, ...rest] = model.split('/');
                    const name = rest.join('/') || model;
                    return (
                      <FallbackRow key={model}>
                        <Grip aria-hidden="true">⋮⋮</Grip>
                        <Rank $primary={idx === 0}>{idx + 1}</Rank>
                        <FallbackModel>
                          <FallbackModelName>{name}</FallbackModelName>
                          <FallbackVendor>{vendor}</FallbackVendor>
                        </FallbackModel>
                        <FallbackOrderTag>
                          {idx === 0 ? 'primary' : `fallback ${idx}`}
                        </FallbackOrderTag>
                      </FallbackRow>
                    );
                  })}
                </FallbackList>
              </Panel>
            )}
          </PrefsSection>
        )}
      </Page>
    </DashboardLayout>
  );
}

// ─── Styled ──────────────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 24px;
  min-width: 0;
`;

const PageHeader = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 4px 0 0;
`;

const HeaderLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const Desc = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const HeaderRight = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
`;

const HeaderNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

// Self-host edition: shown while the local CLI engine is active and these settings are dormant.
const LocalEngineBanner = styled.div`
  display: flex;
  align-items: baseline;
  gap: 14px;
  flex-wrap: wrap;
  padding: 12px 16px;
  background: rgba(255, 176, 0, 0.06);
  border: 1px solid rgba(255, 176, 0, 0.25);
  border-left: 2px solid ${p => p.theme.colors.warning};
  border-radius: 2px;
`;

const BannerBadge = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.warning};
  white-space: nowrap;
`;

const BannerText = styled.p`
  flex: 1 1 320px;
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};

  strong { color: ${p => p.theme.colors.fgStrong}; font-weight: 600; }
`;

const BannerLink = styled.a`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.04em;
  color: ${p => p.theme.colors.info};
  text-decoration: none;
  white-space: nowrap;
  border-radius: 2px;

  &:hover { text-decoration: underline; text-underline-offset: 2px; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

/** Dimmed (but still editable) while the local engine makes these preferences dormant. */
const PrefsSection = styled.div<{ $inactive: boolean }>`
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  ${p => p.$inactive && css`opacity: 0.55;`}
`;

const baseBtnStyles = css`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 6px 12px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostBtn = styled.button`
  ${baseBtnStyles}
  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: rgba(255,255,255,0.02);
  }
`;

const PrimaryBtn = styled.button`
  ${baseBtnStyles}
  color: ${p => p.theme.colors.brand};
  border-color: rgba(0,255,65,0.4);
  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.brandHover};
    border-color: ${p => p.theme.colors.brand};
    background: rgba(0,255,65,0.08);
  }
`;

const ContextRows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ContextRow = styled.div`
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: 24px;
  padding: 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 12px;
  }
`;

const ContextInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const ContextLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
`;

const ContextDesc = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.5;
`;

const DefaultNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgDimmed};
  margin-top: 2px;
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.foreground};
  word-break: break-all;
`;

const ContextControls = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
`;

const SelectorWrap = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const inputStyles = css`
  width: 100%;
  padding: 8px 12px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13.5px;
  color: ${p => p.theme.colors.foreground};
  cursor: pointer;
  transition: border-color 120ms ease;
  appearance: none;
  -webkit-appearance: none;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12'><polyline points='3,5 6,8 9,5' fill='none' stroke='%23838f9b' stroke-width='1.5'/></svg>");
  background-repeat: no-repeat;
  background-position: right 10px center;
  background-size: 10px 10px;
  padding-right: 28px;

  &:focus { outline: none; border-color: ${p => p.theme.colors.focusRing}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const StyledSelect = styled.select`${inputStyles}`;

const ModelTags = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
`;

const ModelTag = styled.span<{ $variant?: 'positive' | 'amber' | 'negative' | 'info' }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 2px 7px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  color: ${p => p.theme.colors.fgMuted};

  ${p => p.$variant === 'positive' && css`
    color: ${p.theme.colors.brand};
    border-color: rgba(0,255,65,0.35);
  `}
  ${p => p.$variant === 'amber' && css`
    color: ${p.theme.colors.terminalAmber};
    border-color: rgba(255,176,0,0.35);
  `}
  ${p => p.$variant === 'negative' && css`
    color: ${p.theme.colors.negative};
    border-color: rgba(255,51,102,0.35);
  `}
  ${p => p.$variant === 'info' && css`
    color: ${p.theme.colors.info};
    border-color: rgba(0,217,255,0.35);
  `}
`;

const TierBanner = styled.div<{ $variant: 'amber' | 'negative' }>`
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  line-height: 1.5;
  ${p => p.$variant === 'negative' && css`
    background: rgba(255,51,102,0.06);
    border: 1px solid rgba(255,51,102,0.3);
    color: ${p.theme.colors.foreground};
    & > span:first-child { color: ${p.theme.colors.negative}; font-family: ${p.theme.typography.fontFamily.mono}; font-weight: ${p.theme.typography.fontWeight.semibold}; }
  `}
  ${p => p.$variant === 'amber' && css`
    background: rgba(255,176,0,0.06);
    border: 1px solid rgba(255,176,0,0.3);
    color: ${p.theme.colors.foreground};
    & > span:first-child { color: ${p.theme.colors.terminalAmber}; font-family: ${p.theme.typography.fontFamily.mono}; font-weight: ${p.theme.typography.fontWeight.semibold}; }
  `}
`;

const OverridesToggle = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.fgMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  padding: 4px 0;
  text-align: left;
  align-self: flex-start;

  &:hover { color: ${p => p.theme.colors.fgStrong}; }
`;

const OverridesGrid = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
`;

const OverrideRow = styled.div`
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 10px;
  align-items: center;

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
    gap: 4px;
  }
`;

const AgentLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const AgentSelect = styled.select`
  ${inputStyles}
  padding: 6px 28px 6px 10px;
  font-size: 13px;
`;

const FallbackList = styled.div`
  display: flex;
  flex-direction: column;
`;

const FALLBACK_COLS = '20px 28px minmax(0, 1fr) 120px';

const FallbackHeaderBar = styled.div`
  display: grid;
  grid-template-columns: ${FALLBACK_COLS};
  gap: 14px;
  align-items: center;
  padding: 10px 18px 10px 14px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  @media (max-width: 768px) {
    display: none;
  }
`;

const RightAligned = styled.span`
  text-align: right;
`;

const FallbackRow = styled.div`
  display: grid;
  grid-template-columns: ${FALLBACK_COLS};
  gap: 14px;
  align-items: center;
  padding: 11px 18px 11px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  @media (max-width: 768px) {
    grid-template-columns: 20px 28px 1fr;
    grid-template-areas: 'grip rank model' '. . order';
    row-gap: 4px;
  }
`;

const Grip = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 18px;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  line-height: 1;
  letter-spacing: -0.12em;
  user-select: none;
  opacity: 0.6;

  @media (max-width: 768px) { grid-area: grip; }
`;

const Rank = styled.span<{ $primary: boolean }>`
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.$primary ? 'rgba(0,255,65,0.05)' : p.theme.colors.surfaceElevated};
  color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.fgMuted};
  ${p => p.$primary && css`border-color: rgba(0,255,65,0.4);`}

  @media (max-width: 768px) { grid-area: rank; }
`;

const FallbackModel = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  @media (max-width: 768px) { grid-area: model; }
`;

const FallbackModelName = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13.5px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  overflow-wrap: anywhere;
`;

const FallbackVendor = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  letter-spacing: 0.02em;
`;

const FallbackOrderTag = styled.span`
  justify-self: end;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};

  @media (max-width: 768px) {
    grid-area: order;
    justify-self: start;
    padding-left: 42px;
  }
`;

const KpiMono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 15px;
  color: ${p => p.theme.colors.fgStrong};
  overflow-wrap: anywhere;
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 32px;
  gap: 10px;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 14px;
  line-height: 1.55;
  max-width: 56ch;
  margin: 0 auto;
`;

const EmptyIcon = styled.div`
  width: 40px;
  height: 40px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 17px;
`;

const EmptyTitle = styled.h3`
  color: ${p => p.theme.colors.fgStrong};
  font-size: 15px;
  margin: 4px 0 0;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const EmptyBody = styled.p`
  margin: 0;
`;

const SkeletonBlock = styled.div`
  display: flex;
  flex-direction: column;
`;

const SkeletonRow = styled.div`
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: 24px;
  align-items: center;
  padding: 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }
`;

const SkelBar = styled.div<{ $w: string; $h: string }>`
  width: ${p => p.$w};
  height: ${p => p.$h};
  border-radius: 3px;
  background: linear-gradient(
    90deg,
    ${p => p.theme.colors.surfaceElevated} 0%,
    ${p => p.theme.colors.borderSubtle} 50%,
    ${p => p.theme.colors.surfaceElevated} 100%
  );
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s linear infinite;

  @keyframes skel-shimmer {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }
`;
