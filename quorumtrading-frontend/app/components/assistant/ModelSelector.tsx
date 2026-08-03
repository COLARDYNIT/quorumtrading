'use client';

import styled from 'styled-components';
import { useAvailableModels } from '@/app/lib/api/hooks';
import { TIER_ORDER, TIER_LABELS, type LLMModel, type ModelTier } from '@/app/lib/types/config';

const Select = styled.select`
  background: ${p => p.theme.colors.background};
  color: ${p => p.theme.colors.textMuted};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: 2px 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  cursor: pointer;
  max-width: 330px;
  min-width: 0;
  text-overflow: ellipsis;

  &:hover {
    border-color: ${p => p.theme.colors.terminalGreen}50;
  }

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
  }

  option {
    background: ${p => p.theme.colors.background};
    color: ${p => p.theme.colors.textPrimary};
  }
`;

function formatCost(costPerMillion: number): string {
  if (costPerMillion === 0) return 'Free';
  if (costPerMillion < 0.01) return '<$0.01/M';
  return `$${costPerMillion.toFixed(2)}/M`;
}

function groupByTier(models: LLMModel[]): Map<ModelTier, LLMModel[]> {
  const grouped = new Map<ModelTier, LLMModel[]>();
  for (const tier of TIER_ORDER) {
    const tierModels = models.filter(m => m.tier === tier);
    if (tierModels.length > 0) {
      grouped.set(tier, tierModels);
    }
  }
  return grouped;
}

interface ModelSelectorProps {
  currentModel: string | null;
  onChange: (model: string) => void;
}

export function ModelSelector({ currentModel, onChange }: ModelSelectorProps) {
  const { data: models } = useAvailableModels('CHAT', ['RECOMMENDED', 'PROVEN']);

  const tierGroups = models ? groupByTier(models) : new Map<ModelTier, LLMModel[]>();
  const hasTiers = tierGroups.size > 0;

  return (
    <Select
      value={currentModel || ''}
      onChange={e => onChange(e.target.value)}
    >
      {hasTiers
        ? Array.from(tierGroups.entries()).map(([tier, tierModels]) => (
            <optgroup key={tier} label={TIER_LABELS[tier]}>
              {tierModels.map(model => (
                <option key={model.id} value={model.id}>
                  {model.name || model.id} ({formatCost(model.promptCostPerMillion)})
                </option>
              ))}
            </optgroup>
          ))
        : models?.map(model => (
            <option key={model.id} value={model.id}>
              {model.name || model.id} ({formatCost(model.promptCostPerMillion)})
            </option>
          ))}
      {!models && currentModel && (
        <option value={currentModel}>{currentModel}</option>
      )}
    </Select>
  );
}
