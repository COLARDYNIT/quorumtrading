'use client';

import { Suspense, useState, useCallback, useMemo, useRef, useEffect, Fragment } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import styled, { useTheme } from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { TerminalButton } from '@/app/components/terminal/TerminalButton';
import { TerminalText } from '@/app/components/terminal/TerminalText';
import { useCreateStrategy, useAvailableModels, useModelPreferences, useEffectiveModels } from '@/app/lib/api/hooks';
import { effectiveModelFor } from '@/app/lib/types/config';
import type { LLMModel } from '@/app/lib/types/config';
import { DEFAULT_MARKDOWN_BODY } from '@/app/lib/constants/strategy-template';
import { searchTickers, type TickerInfo } from '@/app/lib/constants/tickers';
import { fadeInUp, slideInRight, slideInLeft, shadows } from '@/app/styles/animations';
import { tagBase, tagIdentifier } from '@/app/styles/labels';
import toast from 'react-hot-toast';

// ---------------------------------------------------------------------------
// Types & Constants
// ---------------------------------------------------------------------------

type AssetType = 'CRYPTO' | 'STOCK';
type ConsensusMethod = 'MAJORITY' | 'ORCHESTRATOR_OVERRIDE';
type AgentRole = 'TECHNICAL' | 'FUNDAMENTAL' | 'SENTIMENT';
type StepState = 'completed' | 'active' | 'upcoming';

const TOTAL_STEPS = 4;
const ALL_AGENTS: AgentRole[] = ['TECHNICAL', 'FUNDAMENTAL', 'SENTIMENT'];
const CRYPTO_SUGGESTIONS = ['BTC', 'ETH', 'SOL', 'DOGE', 'XRP'];
const STOCK_SUGGESTIONS = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'AMZN', 'TSLA', 'SPY', 'QQQ'];
const DAY_NAMES = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] as const;
const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri',
};

const CRYPTO_INTERVALS = [
  { value: '5m', label: 'Every 5 min' },
  { value: '15m', label: 'Every 15 min' },
  { value: '30m', label: 'Every 30 min' },
  { value: '1h', label: 'Every hour' },
  { value: '2h', label: 'Every 2 hours' },
  { value: '4h', label: 'Every 4 hours' },
  { value: '6h', label: 'Every 6 hours' },
  { value: '12h', label: 'Every 12 hours' },
  { value: '1d', label: 'Daily' },
  { value: '2d', label: 'Every 2 days' },
  { value: '3d', label: 'Every 3 days' },
  { value: '1w', label: 'Weekly' },
];

const BACKTEST_CRYPTO_INTERVALS = CRYPTO_INTERVALS.filter(i =>
  ['6h', '12h', '1d', '2d', '3d', '1w'].includes(i.value),
);

const TIMEZONES = [
  { value: 'America/New_York', label: 'Eastern (New York)' },
  { value: 'America/Chicago', label: 'Central (Chicago)' },
  { value: 'America/Los_Angeles', label: 'Pacific (Los Angeles)' },
  { value: 'Europe/London', label: 'GMT (London)' },
];

/** Maps model tier → theme color key. Resolved at render time via styled-components theme. */
const TIER_COLOR_KEYS: Record<string, string> = {
  RECOMMENDED: 'terminalGreen',
  PROVEN: 'terminalBlue',
  BUDGET: 'terminalAmber',
  EXPERIMENTAL: 'terminalAmber',   // no dedicated orange token
  AVOID: 'terminalPink',
};

const AGENT_INFO = [
  { role: 'TECHNICAL' as AgentRole, title: 'Technical', desc: 'Chart patterns and indicators (RSI, MACD, SMA)' },
  { role: 'FUNDAMENTAL' as AgentRole, title: 'Fundamental', desc: 'Company financials, earnings, valuations' },
  { role: 'SENTIMENT' as AgentRole, title: 'Sentiment', desc: 'News headlines, social media, market mood' },
] as const;

// ---------------------------------------------------------------------------
// Layout Styled Components
// ---------------------------------------------------------------------------

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 24px;
  min-width: 0;
`;

const PageHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0 0;
`;

const Crumb = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};

  & a {
    color: ${p => p.theme.colors.fgMuted};
    text-decoration: none;
    transition: color 120ms ease;
  }
  & a:hover { color: ${p => p.theme.colors.foreground}; }
`;

const CrumbSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const CrumbCurrent = styled.span`
  color: ${p => p.theme.colors.brand};
`;

const PageTitle = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const Subtitle = styled(TerminalText)`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  max-width: 78ch;
`;

const FormCard = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  overflow: hidden;
`;

// ---------------------------------------------------------------------------
// Stepper Styled Components
// ---------------------------------------------------------------------------

const StepperBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${p => p.theme.spacing.lg} ${p => p.theme.spacing.xl};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.background};

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.sm};
  }
`;

const StepperTrack = styled.div`
  display: flex;
  align-items: center;
  gap: 0;
  flex: 1;
  justify-content: center;
`;

const StepGroup = styled.div<{ $clickable: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: ${p => p.$clickable ? 'pointer' : 'default'};
  min-width: 48px;
`;

const StepNode = styled.div<{ $state: StepState }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  border: 2px solid;
  transition: all 0.3s ease;
  flex-shrink: 0;

  background: ${p =>
    p.$state === 'completed' ? p.theme.colors.terminalGreen :
    'transparent'};
  border-color: ${p =>
    p.$state === 'completed' ? p.theme.colors.terminalGreen :
    p.$state === 'active' ? p.theme.colors.terminalGreen :
    p.theme.colors.border};
  color: ${p =>
    p.$state === 'completed' ? p.theme.colors.background :
    p.$state === 'active' ? p.theme.colors.terminalGreen :
    p.theme.colors.textMuted};
  box-shadow: ${p =>
    p.$state === 'active' ? `0 0 12px ${p.theme.colors.terminalGreen}30` :
    'none'};
`;

const StepConnector = styled.div<{ $completed: boolean }>`
  height: 2px;
  width: 48px;
  flex-shrink: 0;
  margin: 0 4px;
  margin-bottom: 22px; /* align with center of StepNode, accounting for label below */
  background: ${p => p.$completed
    ? p.theme.colors.terminalGreen
    : `repeating-linear-gradient(
        90deg,
        ${p.theme.colors.border} 0px,
        ${p.theme.colors.border} 4px,
        transparent 4px,
        transparent 8px
      )`};

  @media (max-width: 640px) {
    width: 24px;
    margin-bottom: 0;
  }
`;

const StepLabelText = styled.span<{ $state: StepState }>`
  font-size: 12px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.$state === 'active' ? p.theme.typography.fontWeight.semibold : p.theme.typography.fontWeight.medium};
  color: ${p =>
    p.$state === 'active' ? p.theme.colors.terminalGreen :
    p.$state === 'completed' ? p.theme.colors.text :
    p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;

  @media (max-width: 640px) {
    display: none;
  }
`;

const ExpertToggleInline = styled.label<{ $active: boolean }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  cursor: pointer;
  flex-shrink: 0;
  margin-left: ${p => p.theme.spacing.lg};
  padding: 6px 14px;
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid ${p => p.$active ? p.theme.colors.terminalBlue : p.theme.colors.border};
  background: ${p => p.$active ? `${p.theme.colors.terminalBlue}10` : 'transparent'};
  transition: all 0.2s ease;

  &:hover {
    border-color: ${p => p.theme.colors.terminalBlue};
    background: ${p => p.theme.colors.terminalBlue}08;
  }

  @media (max-width: 480px) {
    margin-left: ${p => p.theme.spacing.sm};
    padding: 4px 10px;
  }
`;

const ExpertLabel = styled.span<{ $active: boolean }>`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$active ? p.theme.colors.terminalBlue : p.theme.colors.textMuted};
  white-space: nowrap;
  transition: color 0.2s ease;
`;

// ---------------------------------------------------------------------------
// Wizard Body Styled Components
// ---------------------------------------------------------------------------

const WizardBody = styled.div`
  display: grid;
  grid-template-columns: 320px 1fr;
  min-height: 400px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    min-height: auto;
  }
`;

const StepContentArea = styled.div<{ $direction: 'forward' | 'backward' }>`
  padding: ${p => p.theme.spacing.lg} ${p => p.theme.spacing.xl};
  animation: ${p => p.$direction === 'forward' ? slideInRight : slideInLeft} 0.3s ease-out;

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.sm};
  }
`;

const WizardSidebar = styled.div<{ $mobileExpanded?: boolean }>`
  padding: ${p => p.theme.spacing.lg} ${p => p.theme.spacing.lg};
  border-right: 2px solid ${p => p.theme.colors.terminalBlue}30;
  background: ${p => p.theme.colors.background};
  align-self: stretch;

  @media (max-width: 768px) {
    border-right: none;
    border-top: 1px solid ${p => p.theme.colors.border};
    border-bottom: 1px solid ${p => p.theme.colors.border};
    order: -1;
    max-height: ${p => p.$mobileExpanded ? '500px' : '0'};
    padding: ${p => p.$mobileExpanded ? p.theme.spacing.md : '0'} ${p => p.theme.spacing.md};
    overflow: hidden;
    transition: max-height 0.3s ease, padding 0.3s ease;
  }
`;

const MobileTipsToggle = styled.button`
  display: none;
  background: none;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  color: ${p => p.theme.colors.terminalBlue};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  padding: 4px 10px;
  cursor: pointer;
  order: -2;
  margin: ${p => p.theme.spacing.md} ${p => p.theme.spacing.md} 0;

  &:hover {
    background: ${p => p.theme.colors.terminalBlue}10;
  }

  @media (max-width: 768px) {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
`;

const SidebarTitle = styled.div`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.terminalBlue};
  margin-bottom: ${p => p.theme.spacing.md};
`;

const SidebarTip = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
  margin-bottom: ${p => p.theme.spacing.sm};

  &:last-child {
    margin-bottom: 0;
  }
`;

const SidebarTipIcon = styled.span`
  flex-shrink: 0;
  margin-top: 1px;
`;

// ---------------------------------------------------------------------------
// Navigation Styled Components
// ---------------------------------------------------------------------------

const NavigationBar = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.background};
  border-top: 1px solid ${p => p.theme.colors.border};

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.sm};
  }
`;

const StepIndicator = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};

  @media (max-width: 480px) {
    display: none;
  }
`;

// ---------------------------------------------------------------------------
// Form Field Styled Components
// ---------------------------------------------------------------------------

const FieldGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const Label = styled.label`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.text};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const Input = styled.input`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 0 1px ${p => p.theme.colors.terminalGreen}40;
  }

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
    opacity: 0.6;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const Select = styled.select`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  padding-right: 2rem;
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  cursor: pointer;
  color-scheme: dark;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2366bb6a' d='M2 4l4 4 4-4'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 0.5rem center;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 0 1px ${p => p.theme.colors.terminalGreen}40;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  option {
    background: ${p => p.theme.colors.surface};
    color: ${p => p.theme.colors.text};
  }
`;

const Textarea = styled.textarea`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  resize: vertical;
  min-height: 300px;
  line-height: 1.5;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 0 1px ${p => p.theme.colors.terminalGreen}40;
  }

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
    opacity: 0.6;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const HelperText = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.4;
`;

const InlineFields = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
`;

const Toggle = styled.label`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  cursor: pointer;
  padding: ${p => p.theme.spacing.sm} 0;
`;

const ToggleSwitch = styled.div<{ $checked: boolean }>`
  position: relative;
  width: 44px;
  height: 24px;
  background: ${p => p.$checked ? p.theme.colors.terminalGreen : p.theme.colors.border};
  border-radius: 12px;
  transition: background 0.2s ease;
  flex-shrink: 0;

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    left: ${p => p.$checked ? '22px' : '2px'};
    width: 20px;
    height: 20px;
    background: ${p => p.theme.colors.text};
    border-radius: 50%;
    transition: left 0.2s ease;
  }
`;

const ToggleInput = styled.input`
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
`;

const ToggleLabel = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const ToggleTitle = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.text};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const ToggleDescription = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

// Small toggle for stepper bar expert mode
const ToggleSwitchSmall = styled.div<{ $checked: boolean }>`
  position: relative;
  width: 34px;
  height: 18px;
  background: ${p => p.$checked ? p.theme.colors.terminalBlue : p.theme.colors.border};
  border-radius: 9px;
  transition: background 0.2s ease;
  flex-shrink: 0;

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    left: ${p => p.$checked ? '18px' : '2px'};
    width: 14px;
    height: 14px;
    background: ${p => p.theme.colors.text};
    border-radius: 50%;
    transition: left 0.2s ease;
  }
`;

// ---------------------------------------------------------------------------
// Asset Type Styled Components
// ---------------------------------------------------------------------------

const AssetTypeGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
`;

const AssetTypeCard = styled.button<{ $selected: boolean; $accentColor: string }>`
  background: ${p => p.$selected ? `${p.$accentColor}08` : p.theme.colors.background};
  border: 2px solid ${p => p.$selected ? p.$accentColor : p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  padding: ${p => p.theme.spacing.lg};
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: all 0.2s ease;
  box-shadow: ${p => p.$selected ? `0 0 20px ${p.$accentColor}15` : 'none'};

  &:hover {
    border-color: ${p => p.$accentColor};
    background: ${p => `${p.$accentColor}05`};
  }
`;

const AssetTypeTitle = styled.div<{ $accentColor: string; $selected: boolean }>`
  font-size: ${p => p.theme.typography.fontSize.base};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.$selected ? p.$accentColor : p.theme.colors.text};
  margin-bottom: ${p => p.theme.spacing.xs};
  transition: color 0.2s ease;
`;

const AssetTypeDesc = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.4;
`;

// ---------------------------------------------------------------------------
// Chip Styled Components
// ---------------------------------------------------------------------------

const ChipContainer = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.xs};
`;

/** Holds a ticker or an evaluation time — identifiers, rendered verbatim. */
const Chip = styled.span`
  ${tagBase}
  ${tagIdentifier}
  color: ${p => p.theme.colors.terminalGreen};
`;

const ChipRemove = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.textMuted};
  cursor: pointer;
  padding: 0;
  font-size: 15px;
  line-height: 1;
  display: inline-flex;
  align-items: center;

  &:hover {
    color: ${p => p.theme.colors.error};
  }
`;

/** Dashed border is the "not added yet" affordance — deliberately preserved. */
const SuggestionChip = styled.button`
  ${tagBase}
  ${tagIdentifier}
  padding: 4px 10px;
  border-style: dashed;
  color: ${p => p.theme.colors.textMuted};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: ${p => p.theme.colors.terminalGreen};
  }
`;

const DayChip = styled.button<{ $active: boolean }>`
  ${tagBase}
  justify-content: center;
  padding: 6px 12px;
  color: ${p => p.$active ? p.theme.colors.terminalGreen : p.theme.colors.textMuted};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: ${p => p.theme.colors.terminalGreen};
  }
`;

// ---------------------------------------------------------------------------
// Ticker Search Styled Components
// ---------------------------------------------------------------------------

const TickerInputRow = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  align-items: flex-start;
`;

const TickerInput = styled(Input)`
  flex: 1;
`;

const TickerSearchWrapper = styled.div`
  position: relative;
  flex: 1;
`;

const SearchDropdown = styled.div`
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 10;
  margin-top: 4px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.terminalGreen}40;
  border-radius: ${p => p.theme.borderRadius.sm};
  box-shadow: var(--qt-shadow-card-premium);
  max-height: 240px;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.primary}40;
    border-radius: 3px;
  }
`;

const SearchResult = styled.button<{ $highlighted: boolean }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  width: 100%;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.$highlighted ? `${p.theme.colors.terminalGreen}10` : 'transparent'};
  border: none;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  cursor: pointer;
  text-align: left;
  transition: background 0.1s ease;

  &:hover {
    background: ${p => p.theme.colors.terminalGreen}10;
  }
`;

const ResultSymbol = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalGreen};
  min-width: 60px;
`;

const ResultName = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const ResultBadge = styled.span`
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: ${p => p.theme.colors.terminalBlue}15;
  color: ${p => p.theme.colors.terminalBlue};
  border: 1px solid ${p => p.theme.colors.terminalBlue}30;
  flex-shrink: 0;
`;

// ---------------------------------------------------------------------------
// Slider Styled Components
// ---------------------------------------------------------------------------

const SliderRow = styled.div`
  display: grid;
  grid-template-columns: 80px 1fr 50px;
  align-items: center;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    grid-template-columns: 60px 1fr 40px;
    gap: ${p => p.theme.spacing.sm};
  }
`;

const SliderLabel = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.text};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const SliderInput = styled.input`
  width: 100%;
  height: 6px;
  appearance: none;
  background: ${p => p.theme.colors.border};
  border-radius: 3px;
  outline: none;
  cursor: pointer;

  &::-webkit-slider-thumb {
    appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: ${p => p.theme.colors.terminalGreen};
    cursor: pointer;
    border: 2px solid ${p => p.theme.colors.background};
    box-shadow: 0 0 6px ${p => p.theme.colors.terminalGreen}60;
  }

  &::-moz-range-thumb {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: ${p => p.theme.colors.terminalGreen};
    cursor: pointer;
    border: 2px solid ${p => p.theme.colors.background};
    box-shadow: 0 0 6px ${p => p.theme.colors.terminalGreen}60;
  }
`;

const SliderValue = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.terminalGreen};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-align: right;
`;

// ---------------------------------------------------------------------------
// Decision Styled Components
// ---------------------------------------------------------------------------

const DecisionGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
`;

const DecisionCard = styled.button<{ $selected: boolean }>`
  background: ${p => p.$selected ? `${p.theme.colors.terminalGreen}08` : p.theme.colors.background};
  border: 2px solid ${p => p.$selected ? p.theme.colors.terminalGreen : p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  padding: ${p => p.theme.spacing.lg};
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: all 0.2s ease;
  box-shadow: ${p => p.$selected ? `0 0 20px ${p.theme.colors.terminalGreen}15` : 'none'};

  &:hover {
    border-color: ${p => p.theme.colors.terminalGreen};
  }
`;

const DecisionTitle = styled.div<{ $selected: boolean }>`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.$selected ? p.theme.colors.terminalGreen : p.theme.colors.text};
  margin-bottom: ${p => p.theme.spacing.xs};
  transition: color 0.2s ease;
`;

const DecisionDesc = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.4;
`;

// ---------------------------------------------------------------------------
// Advanced / Agent Styled Components
// ---------------------------------------------------------------------------

const CustomizeToggle = styled.button`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  background: none;
  border: none;
  color: ${p => p.theme.colors.terminalBlue};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  cursor: pointer;
  padding: 0;
  margin-top: ${p => p.theme.spacing.sm};

  &:hover {
    text-decoration: underline;
  }
`;

const AgentCard = styled.div<{ $active: boolean }>`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.$active ? `${p.theme.colors.terminalGreen}05` : p.theme.colors.background};
  border: 1px solid ${p => p.$active ? `${p.theme.colors.terminalGreen}20` : p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  transition: all 0.2s ease;
  opacity: ${p => p.$active ? 1 : 0.5};
`;

const AgentCardHeader = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  cursor: pointer;
`;

const AgentCheckbox = styled.div<{ $checked: boolean }>`
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 2px solid ${p => p.$checked ? p.theme.colors.terminalGreen : p.theme.colors.border};
  background: ${p => p.$checked ? `${p.theme.colors.terminalGreen}15` : 'transparent'};
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s ease;
  font-size: 12px;
  color: ${p => p.theme.colors.terminalGreen};
  line-height: 1;
`;

const AgentModelLine = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  margin-left: 26px;
  margin-top: 4px;
`;

const AgentModelName = styled.span<{ $override?: boolean }>`
  color: ${p => p.$override ? p.theme.colors.terminalGreen : p.theme.colors.textMuted};
`;

const AgentModelAction = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.terminalBlue};
  font-size: 12px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  cursor: pointer;
  padding: 0;

  &:hover {
    text-decoration: underline;
  }
`;

const AgentOverrideInput = styled.div`
  margin-left: 26px;
  margin-top: 6px;
`;

const ModelSearchWrapper = styled.div<{ $small?: boolean }>`
  position: relative;
  ${p => p.$small && `
    width: 240px;
    flex-shrink: 0;
    @media (max-width: 640px) {
      width: 100%;
    }
  `}
`;

const ModelDropdown = styled(SearchDropdown)`
  min-width: 360px;
  left: 0;
`;

const ModelTierBadge = styled(ResultBadge)<{ $tier: string }>`
  background: ${p => `${(p.theme.colors as unknown as Record<string, string>)[TIER_COLOR_KEYS[p.$tier]] || p.theme.colors.textDimmed}15`};
  color: ${p => (p.theme.colors as unknown as Record<string, string>)[TIER_COLOR_KEYS[p.$tier]] || p.theme.colors.textDimmed};
  border-color: ${p => `${(p.theme.colors as unknown as Record<string, string>)[TIER_COLOR_KEYS[p.$tier]] || p.theme.colors.textDimmed}30`};
`;

const ModelInputSmall = styled(Input)`
  font-size: ${p => p.theme.typography.fontSize.xs};
  padding: 6px ${p => p.theme.spacing.sm};
  &:disabled { opacity: 0.3; }
`;

// ---------------------------------------------------------------------------
// Agent Context Styled Components
// ---------------------------------------------------------------------------

const AgentContextInfo = styled.div`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-left: 3px solid ${p => p.theme.colors.terminalBlue}60;
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.md};
  margin-bottom: ${p => p.theme.spacing.md};
`;

const AgentContextTitle = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.terminalBlue};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const AgentContextList = styled.ul`
  margin: 0;
  padding: 0 0 0 ${p => p.theme.spacing.md};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.6;

  li {
    margin-bottom: 2px;
  }

  strong {
    color: ${p => p.theme.colors.text};
  }
`;

// ---------------------------------------------------------------------------
// Schedule Styled Components
// ---------------------------------------------------------------------------

const ScheduleSubHeader = styled.div`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.terminalBlue};
  margin-top: ${p => p.theme.spacing.lg};
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const ScheduleHint = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  margin-bottom: ${p => p.theme.spacing.sm};
  display: block;
`;

const MarketHoursFields = styled.div<{ $visible: boolean }>`
  margin-top: ${p => p.theme.spacing.sm};
  opacity: ${p => p.$visible ? 1 : 0};
  max-height: ${p => p.$visible ? '200px' : '0'};
  overflow: hidden;
  transition: opacity 0.3s ease, max-height 0.3s ease;
`;

// ---------------------------------------------------------------------------
// Review Styled Components
// ---------------------------------------------------------------------------

const ReviewCard = styled.div`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  padding: ${p => p.theme.spacing.md};
`;

const ReviewCardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${p => p.theme.spacing.sm};
  padding-bottom: ${p => p.theme.spacing.sm};
  border-bottom: 1px solid ${p => p.theme.colors.border}40;
`;

const ReviewCardTitle = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.text};
`;

const ReviewEditLink = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.terminalBlue};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  cursor: pointer;
  padding: 0;

  &:hover {
    text-decoration: underline;
  }
`;

const ReviewRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 4px 0;
  gap: ${p => p.theme.spacing.md};
`;

const ReviewRowLabel = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  flex-shrink: 0;
  min-width: 100px;
`;

const ReviewRowValue = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.text};
  text-align: right;
  word-break: break-word;
`;

const YamlToggle = styled.button`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  background: none;
  border: none;
  color: ${p => p.theme.colors.terminalBlue};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  cursor: pointer;
  padding: 0;

  &:hover {
    text-decoration: underline;
  }
`;

const YamlPreview = styled.pre`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.md};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  margin: 0;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.primary}40;
    border-radius: 3px;
  }
`;

const InstructionsPreview = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
`;

// ---------------------------------------------------------------------------
// buildStrategyMarkdown
// ---------------------------------------------------------------------------

function buildStrategyMarkdown(state: {
  consensusMethod: ConsensusMethod;
  assetType: AssetType;
  tickers: string[];
  cryptoInterval: string;
  tradingDays: string[];
  evaluationTimes: string[];
  marketOpen: string;
  marketClose: string;
  timezone: string;
  reserveCash: number;
  positionLimits: Record<string, number>;
  consensusThreshold: number;
  agents: AgentRole[];
  backtestCryptoInterval: string;
  backtestTradingDays: string[];
  backtestEvaluationTimes: string[];
  llmModel: string;
  llmModels: Record<string, string>;
  positionCooldown: string;
  markdownBody: string;
}): string {
  const lines: string[] = ['---'];

  lines.push(`consensus_method: ${state.consensusMethod}`);
  lines.push('');

  // Tickers
  const tickerKey = state.assetType === 'CRYPTO' ? 'crypto_tickers' : 'stock_tickers';
  lines.push(`${tickerKey}:`);
  for (const t of state.tickers) {
    lines.push(`  - ${t}`);
  }
  lines.push('');

  // Schedule
  lines.push('live_schedule:');
  if (state.assetType === 'CRYPTO') {
    lines.push(`  interval: ${state.cryptoInterval}`);
  } else {
    lines.push(`  timezone: "${state.timezone}"`);
    const days = state.tradingDays.map(d => d).join(', ');
    lines.push(`  trading_days: [${days}]`);
    const times = state.evaluationTimes.map(t => `"${t}"`).join(', ');
    lines.push(`  evaluation_times: [${times}]`);
    lines.push(`  market_open: "${state.marketOpen}"`);
    lines.push(`  market_close: "${state.marketClose}"`);
  }
  lines.push('');

  // Backtest schedule
  lines.push('backtest_schedule:');
  if (state.assetType === 'CRYPTO') {
    lines.push(`  interval: ${state.backtestCryptoInterval}`);
  } else {
    lines.push(`  timezone: "${state.timezone}"`);
    const btDays = state.backtestTradingDays.join(', ');
    lines.push(`  trading_days: [${btDays}]`);
    const btTimes = state.backtestEvaluationTimes.map(t => `"${t}"`).join(', ');
    lines.push(`  evaluation_times: [${btTimes}]`);
    lines.push(`  market_open: "${state.marketOpen}"`);
    lines.push(`  market_close: "${state.marketClose}"`);
  }
  lines.push('');

  // Reserve cash
  lines.push(`reserve_cash_percentage: ${(state.reserveCash / 100).toFixed(2)}`);
  lines.push('');

  // Position limits
  if (Object.keys(state.positionLimits).length > 0) {
    lines.push('position_limits:');
    for (const [ticker, pct] of Object.entries(state.positionLimits)) {
      lines.push(`  ${ticker}: ${(pct / 100).toFixed(2)}`);
    }
    lines.push('');
  }

  // Consensus threshold (only if non-default)
  if (state.consensusThreshold !== 60) {
    lines.push(`consensus_threshold: ${(state.consensusThreshold / 100).toFixed(2)}`);
    lines.push('');
  }

  // Agents (only if not all 4)
  if (state.agents.length < ALL_AGENTS.length) {
    lines.push('agents:');
    for (const a of state.agents) {
      lines.push(`  - ${a}`);
    }
    lines.push('');
  }

  // Optional fields
  if (state.llmModel.trim()) {
    lines.push(`llm_model: "${state.llmModel.trim()}"`);
    lines.push('');
  }

  const activeOverrides = Object.entries(state.llmModels).filter(([, v]) => v.trim());
  if (activeOverrides.length > 0) {
    lines.push('llm_models:');
    for (const [agent, model] of activeOverrides) {
      lines.push(`  ${agent}: "${model.trim()}"`);
    }
    lines.push('');
  }

  if (state.positionCooldown.trim()) {
    lines.push(`position_cooldown: ${state.positionCooldown.trim()}`);
    lines.push('');
  }

  lines.push('---');
  lines.push('');
  lines.push(state.markdownBody);

  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Model Autocomplete
// ---------------------------------------------------------------------------

function ModelAutocomplete({
  value,
  onChange,
  placeholder = 'Search models...',
  disabled,
  models,
  small,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  models: LLMModel[];
  small?: boolean;
}) {
  const [showDrop, setShowDrop] = useState(false);
  const [hlIndex, setHlIndex] = useState(-1);
  const wrapRef = useRef<HTMLDivElement>(null);

  const results = useMemo(() => {
    const q = value.toLowerCase().trim();
    if (!q) return models.slice(0, 8);
    return models.filter(m =>
      m.id.toLowerCase().includes(q) ||
      m.name.toLowerCase().includes(q) ||
      (m.provider && m.provider.toLowerCase().includes(q))
    ).slice(0, 8);
  }, [value, models]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setShowDrop(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const select = useCallback((model: LLMModel) => {
    onChange(model.id);
    setShowDrop(false);
    setHlIndex(-1);
  }, [onChange]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (hlIndex >= 0 && hlIndex < results.length) {
        select(results[hlIndex]);
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHlIndex(prev => Math.min(prev + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHlIndex(prev => Math.max(prev - 1, -1));
    } else if (e.key === 'Escape') {
      setShowDrop(false);
      setHlIndex(-1);
    }
  }, [hlIndex, results, select]);

  const InputComponent = small ? ModelInputSmall : Input;

  return (
    <ModelSearchWrapper ref={wrapRef} $small={small}>
      <InputComponent
        type="text"
        value={value}
        onChange={e => {
          onChange(e.target.value);
          setShowDrop(true);
          setHlIndex(-1);
        }}
        onFocus={() => setShowDrop(true)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled}
        autoComplete="off"
      />
      {showDrop && !disabled && results.length > 0 && (
        <ModelDropdown>
          {results.map((m, i) => (
            <SearchResult
              key={m.id}
              type="button"
              $highlighted={i === hlIndex}
              onMouseDown={e => { e.preventDefault(); select(m); }}
              onMouseEnter={() => setHlIndex(i)}
            >
              <ResultSymbol style={{ minWidth: '40px' }}>{m.provider ?? ''}</ResultSymbol>
              <ResultName>{m.name || m.id}</ResultName>
              {m.tier && <ModelTierBadge $tier={m.tier}>{m.tier}</ModelTierBadge>}
            </SearchResult>
          ))}
        </ModelDropdown>
      )}
    </ModelSearchWrapper>
  );
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

export default function NewStrategyPage() {
  return (
    <Suspense>
      <NewStrategyPageContent />
    </Suspense>
  );
}

function NewStrategyPageContent() {
  const theme = useTheme();
  const router = useRouter();
  const searchParams = useSearchParams();
  const createMutation = useCreateStrategy();
  const { data: modelsData } = useAvailableModels();
  const { data: preferences } = useModelPreferences();
  // The system default is computed by the backend for the ACTIVE engine, never restated here.
  const { data: effectiveModels } = useEffectiveModels();
  const availableModels = useMemo(() => {
    const tierOrder: Record<string, number> = { RECOMMENDED: 0, PROVEN: 1, BUDGET: 2, EXPERIMENTAL: 3, AVOID: 4 };
    return (modelsData ?? []).sort((a, b) =>
      (tierOrder[a.tier ?? 'EXPERIMENTAL'] ?? 3) - (tierOrder[b.tier ?? 'EXPERIMENTAL'] ?? 3)
    );
  }, [modelsData]);

  // Resolve the effective base model for backtesting context (most common use)
  // Fetched from the backend: what BACKTESTING will actually run on the active engine.
  const systemDefault = effectiveModelFor(effectiveModels, 'BACKTESTING');
  const accountDefault = preferences?.backtesting ?? null;
  const accountHasOverride = !!accountDefault;

  // ---- Wizard state ----
  const [currentStep, setCurrentStep] = useState(0);
  const [direction, setDirection] = useState<'forward' | 'backward'>('forward');
  const [showYaml, setShowYaml] = useState(false);
  const [showMobileTips, setShowMobileTips] = useState(false);

  // ---- Form state ----
  const [name, setName] = useState('');
  const [assetType, setAssetType] = useState<AssetType | null>(null);
  const [tickers, setTickers] = useState<string[]>([]);
  const [tickerInput, setTickerInput] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Schedule - Crypto
  const [cryptoInterval, setCryptoInterval] = useState('1h');
  const [backtestCryptoInterval, setBacktestCryptoInterval] = useState('1w');

  // Schedule - Stock
  const [tradingDays, setTradingDays] = useState<string[]>([...DAY_NAMES]);
  const [evaluationTimes, setEvaluationTimes] = useState<string[]>(['10:00', '14:00']);
  const [timeInput, setTimeInput] = useState('');
  const [marketOpen, setMarketOpen] = useState('09:30');
  const [marketClose, setMarketClose] = useState('16:00');
  const [timezone, setTimezone] = useState('America/New_York');
  const [showMarketHours, setShowMarketHours] = useState(false);
  const [backtestTradingDays, setBacktestTradingDays] = useState<string[]>(['WEDNESDAY']);
  const [backtestEvaluationTimes, setBacktestEvaluationTimes] = useState<string[]>(['12:00']);
  const [backtestTimeInput, setBacktestTimeInput] = useState('');

  // Decision
  const [consensusMethod, setConsensusMethod] = useState<ConsensusMethod>('MAJORITY');

  // Risk
  const [reserveCash, setReserveCash] = useState(0);
  const [positionLimits, setPositionLimits] = useState<Record<string, number>>({});

  // Expert mode
  const [expertMode, setExpertMode] = useState(false);

  // Advanced
  const [agents, setAgents] = useState<AgentRole[]>([...ALL_AGENTS]);
  const [consensusThreshold, setConsensusThreshold] = useState(60);
  const [llmModel, setLlmModel] = useState('');
  const [llmModels, setLlmModels] = useState<Record<string, string>>({});
  const [expandedOverrides, setExpandedOverrides] = useState<Set<string>>(new Set());
  const [positionCooldown, setPositionCooldown] = useState('');

  // Instructions
  const [markdownBody, setMarkdownBody] = useState('');

  // Prefill from URL query params (?stock=AAPL or ?crypto=BTC)
  // When prefilled, the asset section is locked — user can't change ticker or type
  const [prefilledTicker, setPrefilledTicker] = useState<{ ticker: string; type: AssetType } | null>(null);
  const challengeId = searchParams.get('challengeId');

  useEffect(() => {
    const stockParam = searchParams.get('stock');
    const cryptoParam = searchParams.get('crypto');

    if (cryptoParam) {
      const t = cryptoParam.toUpperCase();
      setAssetType('CRYPTO');
      setTickers([t]);
      setPrefilledTicker({ ticker: t, type: 'CRYPTO' });
    } else if (stockParam) {
      const t = stockParam.toUpperCase();
      setAssetType('STOCK');
      setTickers([t]);
      setPrefilledTicker({ ticker: t, type: 'STOCK' });
    }
  }, [searchParams]);

  const isLoading = createMutation.isPending;

  // ---- Step labels ----
  const stepLabels = useMemo(() =>
    expertMode
      ? ['Assets', 'Configuration', 'Instructions', 'Review']
      : ['Assets', 'Decision', 'Instructions', 'Review'],
    [expertMode]
  );

  // ---- Step validation ----
  const canContinue = useMemo(() => {
    switch (currentStep) {
      case 0:
        return assetType !== null && tickers.length > 0;
      case 1:
        if (expertMode && assetType === 'STOCK') {
          return tradingDays.length > 0 && evaluationTimes.length > 0
            && backtestTradingDays.length > 0 && backtestEvaluationTimes.length > 0;
        }
        return true;
      case 2:
        return markdownBody.trim().length > 0;
      default:
        return true;
    }
  }, [currentStep, name, assetType, tickers, expertMode, tradingDays, evaluationTimes, backtestTradingDays, backtestEvaluationTimes, markdownBody]);

  // ---- Navigation ----
  const goNext = useCallback(() => {
    if (currentStep < TOTAL_STEPS - 1 && canContinue) {
      setDirection('forward');
      setCurrentStep(prev => prev + 1);
      setShowMobileTips(false);
    }
  }, [currentStep, canContinue]);

  const goBack = useCallback(() => {
    if (currentStep > 0) {
      setDirection('backward');
      setCurrentStep(prev => prev - 1);
      setShowMobileTips(false);
    }
  }, [currentStep]);

  const goToStep = useCallback((step: number) => {
    if (step < currentStep) {
      setDirection('backward');
      setCurrentStep(step);
      setShowMobileTips(false);
    }
  }, [currentStep]);

  // ---- Search results ----
  const searchResults = useMemo(() => {
    if (!assetType || !tickerInput.trim()) return [];
    return searchTickers(tickerInput, assetType).filter(r => !tickers.includes(r.symbol));
  }, [tickerInput, assetType, tickers]);

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // ---- Handlers ----

  const doAddTicker = useCallback((symbol: string) => {
    const val = symbol.trim().toUpperCase();
    if (val && !tickers.includes(val)) {
      const newCount = tickers.length + 1;
      const limit = newCount === 1 ? 100 : 50;
      setTickers(prev => [...prev, val]);
      setPositionLimits(prev => {
        const next: Record<string, number> = {};
        for (const t of tickers) next[t] = limit;
        next[val] = limit;
        return next;
      });
    }
    setTickerInput('');
    setShowDropdown(false);
    setHighlightedIndex(-1);
  }, [tickers]);

  const addTicker = useCallback(() => {
    if (highlightedIndex >= 0 && highlightedIndex < searchResults.length) {
      doAddTicker(searchResults[highlightedIndex].symbol);
      return;
    }
    if (searchResults.length === 1) {
      doAddTicker(searchResults[0].symbol);
      return;
    }
    doAddTicker(tickerInput);
  }, [tickerInput, highlightedIndex, searchResults, doAddTicker]);

  const selectSearchResult = useCallback((result: TickerInfo) => {
    doAddTicker(result.symbol);
  }, [doAddTicker]);

  const handleTickerKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      addTicker();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex(prev => Math.min(prev + 1, searchResults.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex(prev => Math.max(prev - 1, -1));
    } else if (e.key === 'Escape') {
      setShowDropdown(false);
      setHighlightedIndex(-1);
    }
  }, [addTicker, searchResults.length]);

  const removeTicker = useCallback((ticker: string) => {
    setTickers(prev => {
      const remaining = prev.filter(t => t !== ticker);
      const limit = remaining.length === 1 ? 100 : 50;
      setPositionLimits(() => {
        const next: Record<string, number> = {};
        for (const t of remaining) next[t] = limit;
        return next;
      });
      return remaining;
    });
  }, []);

  const addSuggestion = useCallback((ticker: string) => {
    doAddTicker(ticker);
  }, [doAddTicker]);

  const toggleDay = useCallback((day: string) => {
    setTradingDays(prev =>
      prev.includes(day) ? prev.filter(d => d !== day) : [...prev, day]
    );
  }, []);

  const addTime = useCallback(() => {
    const val = timeInput.trim();
    if (val && /^\d{2}:\d{2}$/.test(val) && !evaluationTimes.includes(val)) {
      setEvaluationTimes(prev => [...prev, val].sort());
    }
    setTimeInput('');
  }, [timeInput, evaluationTimes]);

  const removeTime = useCallback((time: string) => {
    setEvaluationTimes(prev => prev.filter(t => t !== time));
  }, []);

  const toggleBacktestDay = useCallback((day: string) => {
    setBacktestTradingDays(prev =>
      prev.includes(day) ? prev.filter(d => d !== day) : [...prev, day]
    );
  }, []);

  const addBacktestTime = useCallback(() => {
    const val = backtestTimeInput.trim();
    if (val && /^\d{2}:\d{2}$/.test(val) && !backtestEvaluationTimes.includes(val)) {
      setBacktestEvaluationTimes(prev => [...prev, val].sort());
    }
    setBacktestTimeInput('');
  }, [backtestTimeInput, backtestEvaluationTimes]);

  const removeBacktestTime = useCallback((time: string) => {
    setBacktestEvaluationTimes(prev => prev.filter(t => t !== time));
  }, []);

  const toggleAgent = useCallback((agent: AgentRole) => {
    setAgents(prev => {
      if (prev.includes(agent)) {
        if (prev.length <= 1) {
          toast.error('At least one agent must be active');
          return prev;
        }
        return prev.filter(a => a !== agent);
      }
      return [...prev, agent];
    });
  }, []);

  const handlePositionLimit = useCallback((ticker: string, value: number) => {
    setPositionLimits(prev => ({ ...prev, [ticker]: value }));
  }, []);

  const handleAgentModel = useCallback((agent: string, value: string) => {
    setLlmModels(prev => ({ ...prev, [agent]: value }));
  }, []);

  const handleAssetTypeChange = useCallback((type: AssetType) => {
    if (type !== assetType) {
      setAssetType(type);
      setTickers([]);
      setPositionLimits({});
      setTickerInput('');
    }
  }, [assetType]);

  const generateName = useCallback(() => {
    const type = assetType === 'CRYPTO' ? 'Crypto' : 'Stock';
    if (tickers.length <= 3) {
      return `${tickers.join(', ')} ${type} Strategy`;
    }
    return `${type} Strategy (${tickers.length} assets)`;
  }, [assetType, tickers]);

  const handleSave = async () => {
    if (!assetType) {
      toast.error('Please select an asset type');
      return;
    }
    if (tickers.length === 0) {
      toast.error('Add at least one ticker');
      return;
    }
    if (assetType === 'STOCK' && tradingDays.length === 0) {
      toast.error('Select at least one trading day');
      return;
    }
    if (assetType === 'STOCK' && evaluationTimes.length === 0) {
      toast.error('Add at least one live evaluation time');
      return;
    }
    if (assetType === 'STOCK' && backtestTradingDays.length === 0) {
      toast.error('Select at least one backtest trading day');
      return;
    }
    if (assetType === 'STOCK' && backtestEvaluationTimes.length === 0) {
      toast.error('Add at least one backtest evaluation time');
      return;
    }

    const markdownContent = buildStrategyMarkdown({
      consensusMethod,
      assetType,
      tickers,
      cryptoInterval,
      tradingDays,
      evaluationTimes,
      marketOpen,
      marketClose,
      timezone,
      backtestCryptoInterval,
      backtestTradingDays,
      backtestEvaluationTimes,
      reserveCash,
      positionLimits,
      consensusThreshold,
      agents,
      llmModel,
      llmModels,
      positionCooldown,
      markdownBody,
    });

    try {
      const result = await createMutation.mutateAsync({
        name: name.trim() || generateName(),
        markdownContent,
      });
      if (challengeId) {
        // Redirect back to challenge with the new strategy pre-selected for joining
        router.push(`/challenges/${challengeId}?joinWith=${result.id}`);
      } else {
        router.push(`/strategies/${result.id}`);
      }
    } catch {
      // Error toast handled by mutation hook
    }
  };

  const handleCancel = () => {
    const hasChanges = name.trim().length > 0 || assetType !== null || tickers.length > 0 || markdownBody.trim().length > 0;
    if (hasChanges) {
      const confirmed = window.confirm('You have unsaved changes. Are you sure you want to leave?');
      if (!confirmed) return;
    }
    router.push('/strategies');
  };

  const suggestions = assetType === 'CRYPTO' ? CRYPTO_SUGGESTIONS : STOCK_SUGGESTIONS;

  // ---- Generated YAML for review ----
  const generatedYaml = useMemo(() => {
    if (!assetType || tickers.length === 0) return '';
    return buildStrategyMarkdown({
      consensusMethod,
      assetType,
      tickers,
      cryptoInterval,
      tradingDays,
      evaluationTimes,
      marketOpen,
      marketClose,
      timezone,
      backtestCryptoInterval,
      backtestTradingDays,
      backtestEvaluationTimes,
      reserveCash,
      positionLimits,
      consensusThreshold,
      agents,
      llmModel,
      llmModels,
      positionCooldown,
      markdownBody,
    });
  }, [consensusMethod, assetType, tickers, cryptoInterval, tradingDays, evaluationTimes,
    marketOpen, marketClose, timezone, backtestCryptoInterval, backtestTradingDays,
    backtestEvaluationTimes, reserveCash, positionLimits, consensusThreshold, agents,
    llmModel, llmModels, positionCooldown, markdownBody]);

  // ---- Step state helper ----
  const getStepState = (index: number): StepState => {
    if (index < currentStep) return 'completed';
    if (index === currentStep) return 'active';
    return 'upcoming';
  };

  // =========================================================================
  // Render: Step Content
  // =========================================================================

  const renderStepContent = () => {
    switch (currentStep) {
      // --- Step 0: Assets ---
      case 0:
        return (
          <FieldGroup>
            <Field>
              <Label>Strategy name <span style={{ fontWeight: 400, opacity: 0.5 }}>(optional)</span></Label>
              <Input
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="Auto-generated if left empty"
                disabled={isLoading}
              />
            </Field>

            {prefilledTicker ? (
              /* Locked asset — from challenge or URL param */
              <Field>
                <div style={{
                  background: theme.colors.surface,
                  border: `1px solid ${prefilledTicker.type === 'CRYPTO' ? theme.colors.terminalMint : theme.colors.terminalBlue}33`,
                  padding: theme.spacing.lg,
                }}>
                  <div style={{
                    fontFamily: theme.typography.fontFamily.mono,
                    fontSize: theme.typography.fontSize.xs,
                    color: theme.colors.textMuted,
                    textTransform: 'uppercase' as const,
                    letterSpacing: '0.15em',
                    marginBottom: theme.spacing.sm,
                  }}>
                    Strategy for
                  </div>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: theme.spacing.md,
                  }}>
                    <span style={{
                      fontFamily: theme.typography.fontFamily.mono,
                      fontSize: theme.typography.fontSize['2xl'],
                      fontWeight: 700,
                      color: prefilledTicker.type === 'CRYPTO' ? theme.colors.terminalMint : theme.colors.terminalBlue,
                    }}>
                      {prefilledTicker.ticker}
                    </span>
                    <span style={{
                      fontFamily: theme.typography.fontFamily.mono,
                      fontSize: theme.typography.fontSize.xs,
                      color: theme.colors.textMuted,
                      padding: '2px 8px',
                      border: `1px solid ${theme.colors.border}`,
                      textTransform: 'uppercase' as const,
                    }}>
                      {prefilledTicker.type === 'CRYPTO' ? 'Crypto · 24/7' : 'Stock · Market hours'}
                    </span>
                  </div>
                </div>
              </Field>
            ) : (
              /* Normal asset type + ticker selection */
              <>
                <Field>
                  <Label>Asset type</Label>
                  <AssetTypeGrid>
                    <AssetTypeCard
                      type="button"
                      $selected={assetType === 'CRYPTO'}
                      $accentColor={theme.colors.terminalMint}
                      onClick={() => handleAssetTypeChange('CRYPTO')}
                      disabled={isLoading}
                    >
                      <AssetTypeTitle $accentColor={theme.colors.terminalMint} $selected={assetType === 'CRYPTO'}>
                        CRYPTO
                      </AssetTypeTitle>
                      <AssetTypeDesc>24/7 trading. Bitcoin, Ethereum, and more.</AssetTypeDesc>
                    </AssetTypeCard>
                    <AssetTypeCard
                      type="button"
                      $selected={assetType === 'STOCK'}
                      $accentColor={theme.colors.terminalBlue}
                      onClick={() => handleAssetTypeChange('STOCK')}
                      disabled={isLoading}
                    >
                      <AssetTypeTitle $accentColor={theme.colors.terminalBlue} $selected={assetType === 'STOCK'}>
                        STOCKS
                      </AssetTypeTitle>
                      <AssetTypeDesc>Scheduled trading during market hours.</AssetTypeDesc>
                    </AssetTypeCard>
                  </AssetTypeGrid>
                </Field>

                {assetType && (
                  <>
                    <Field>
                      <Label>Search by name or symbol</Label>
                      <TickerInputRow>
                        <TickerSearchWrapper ref={dropdownRef}>
                          <TickerInput
                            type="text"
                            value={tickerInput}
                            onChange={e => {
                              setTickerInput(e.target.value);
                              setShowDropdown(true);
                              setHighlightedIndex(-1);
                            }}
                            onKeyDown={handleTickerKeyDown}
                            onFocus={() => tickerInput.trim() && setShowDropdown(true)}
                            placeholder={assetType === 'CRYPTO' ? 'e.g., BTC or Bitcoin' : 'e.g., AAPL, Google, or SPY'}
                            disabled={isLoading}
                            autoComplete="off"
                          />
                          {showDropdown && searchResults.length > 0 && (
                            <SearchDropdown>
                              {searchResults.map((r, i) => (
                                <SearchResult
                                  key={r.symbol}
                                  type="button"
                                  $highlighted={i === highlightedIndex}
                                  onMouseDown={e => { e.preventDefault(); selectSearchResult(r); }}
                                  onMouseEnter={() => setHighlightedIndex(i)}
                                >
                                  <ResultSymbol>{r.symbol}</ResultSymbol>
                                  <ResultName>{r.name}</ResultName>
                                  {r.type === 'ETF' && <ResultBadge>ETF</ResultBadge>}
                                </SearchResult>
                              ))}
                            </SearchDropdown>
                          )}
                        </TickerSearchWrapper>
                        <TerminalButton
                          type="button"
                          variant="secondary"
                          onClick={addTicker}
                          disabled={isLoading || !tickerInput.trim()}
                        >
                          Add
                        </TerminalButton>
                      </TickerInputRow>
                    </Field>

                    <Field>
                      <HelperText>Popular:</HelperText>
                      <ChipContainer>
                        {suggestions
                          .filter(s => !tickers.includes(s))
                          .map(s => (
                            <SuggestionChip key={s} type="button" onClick={() => addSuggestion(s)}>
                              {s}
                            </SuggestionChip>
                          ))}
                      </ChipContainer>
                    </Field>

                    {tickers.length > 0 && (
                      <Field>
                        <ChipContainer>
                          {tickers.map(t => (
                            <Chip key={t}>
                              {t}
                              <ChipRemove type="button" onClick={() => removeTicker(t)}>&times;</ChipRemove>
                            </Chip>
                          ))}
                        </ChipContainer>
                      </Field>
                    )}

                    <HelperText>
                      Type a company name (Google, Tesla) or symbol (AAPL, SPY). Includes stocks and ETFs for stock mode.
                    </HelperText>
                  </>
                )}
              </>
            )}
          </FieldGroup>
        );

      // --- Step 1: Decision (basic) / Configuration (expert) ---
      case 1:
        return (
          <FieldGroup>
            {/* Consensus Method — always shown */}
            <Field>
              <Label>Decision method</Label>
              <DecisionGrid>
                <DecisionCard
                  type="button"
                  $selected={consensusMethod === 'MAJORITY'}
                  onClick={() => setConsensusMethod('MAJORITY')}
                  disabled={isLoading}
                >
                  <DecisionTitle $selected={consensusMethod === 'MAJORITY'}>
                    Majority Vote
                  </DecisionTitle>
                  <DecisionDesc>
                    Each AI agent votes and the majority wins. Simple and transparent. Ties default to HOLD.
                  </DecisionDesc>
                </DecisionCard>
                <DecisionCard
                  type="button"
                  $selected={consensusMethod === 'ORCHESTRATOR_OVERRIDE'}
                  onClick={() => setConsensusMethod('ORCHESTRATOR_OVERRIDE')}
                  disabled={isLoading}
                >
                  <DecisionTitle $selected={consensusMethod === 'ORCHESTRATOR_OVERRIDE'}>
                    AI Orchestrator
                  </DecisionTitle>
                  <DecisionDesc>
                    A senior AI reviews all recommendations and makes the final call. Better for nuanced markets but takes longer to reach consensus.
                  </DecisionDesc>
                </DecisionCard>
              </DecisionGrid>
            </Field>

            {/* Expert mode: Schedule */}
            {expertMode && (
              <>
                <ScheduleSubHeader style={{ marginTop: '24px' }}>Schedule</ScheduleSubHeader>
                <ScheduleHint>
                  Configure how often the AI evaluates. Backtests run less frequently to save AI budget.
                </ScheduleHint>

                {assetType === 'CRYPTO' ? (
                  <>
                    <ScheduleSubHeader style={{ fontSize: '12px', marginTop: '8px' }}>Live trading</ScheduleSubHeader>
                    <Field>
                      <Label>Evaluation interval</Label>
                      <Select
                        value={cryptoInterval}
                        onChange={e => setCryptoInterval(e.target.value)}
                        disabled={isLoading}
                      >
                        {CRYPTO_INTERVALS.map(i => (
                          <option key={i.value} value={i.value}>{i.label}</option>
                        ))}
                      </Select>
                    </Field>

                    <ScheduleSubHeader style={{ fontSize: '12px', marginTop: '8px' }}>Backtesting</ScheduleSubHeader>
                    <ScheduleHint>Less frequent = fewer AI calls = lower cost.</ScheduleHint>
                    <Field>
                      <Label>Backtest interval</Label>
                      <Select
                        value={backtestCryptoInterval}
                        onChange={e => setBacktestCryptoInterval(e.target.value)}
                        disabled={isLoading}
                      >
                        {BACKTEST_CRYPTO_INTERVALS.map(i => (
                          <option key={i.value} value={i.value}>{i.label}</option>
                        ))}
                      </Select>
                    </Field>
                  </>
                ) : (
                  <>
                    <ScheduleSubHeader style={{ fontSize: '12px', marginTop: '8px' }}>Live trading</ScheduleSubHeader>
                    <Field>
                      <Label>Trading days</Label>
                      <ChipContainer>
                        {DAY_NAMES.map(d => (
                          <DayChip
                            key={d}
                            type="button"
                            $active={tradingDays.includes(d)}
                            onClick={() => toggleDay(d)}
                            disabled={isLoading}
                          >
                            {DAY_LABELS[d]}
                          </DayChip>
                        ))}
                      </ChipContainer>
                    </Field>

                    <Field>
                      <Label>Evaluation times</Label>
                      <TickerInputRow>
                        <TickerInput
                          type="text"
                          value={timeInput}
                          onChange={e => setTimeInput(e.target.value)}
                          onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTime(); } }}
                          placeholder="e.g., 10:00"
                          disabled={isLoading}
                        />
                        <TerminalButton
                          type="button"
                          variant="secondary"
                          onClick={addTime}
                          disabled={isLoading || !timeInput.trim()}
                        >
                          Add
                        </TerminalButton>
                      </TickerInputRow>
                      {evaluationTimes.length > 0 && (
                        <ChipContainer>
                          {evaluationTimes.map(t => (
                            <Chip key={t}>
                              {t}
                              <ChipRemove type="button" onClick={() => removeTime(t)}>&times;</ChipRemove>
                            </Chip>
                          ))}
                        </ChipContainer>
                      )}
                      <HelperText>Times in market timezone, must fall within market hours.</HelperText>
                    </Field>

                    <Field>
                      <Label>Timezone</Label>
                      <Select
                        value={timezone}
                        onChange={e => setTimezone(e.target.value)}
                        disabled={isLoading}
                      >
                        {TIMEZONES.map(tz => (
                          <option key={tz.value} value={tz.value}>{tz.label}</option>
                        ))}
                      </Select>
                    </Field>

                    <CustomizeToggle type="button" onClick={() => setShowMarketHours(!showMarketHours)}>
                      {showMarketHours ? '- Hide' : '+ Customize'} market hours
                    </CustomizeToggle>
                    <MarketHoursFields $visible={showMarketHours}>
                      <InlineFields>
                        <Field>
                          <Label>Market open</Label>
                          <Input
                            type="text"
                            value={marketOpen}
                            onChange={e => setMarketOpen(e.target.value)}
                            placeholder="09:30"
                            disabled={isLoading}
                          />
                        </Field>
                        <Field>
                          <Label>Market close</Label>
                          <Input
                            type="text"
                            value={marketClose}
                            onChange={e => setMarketClose(e.target.value)}
                            placeholder="16:00"
                            disabled={isLoading}
                          />
                        </Field>
                      </InlineFields>
                    </MarketHoursFields>

                    <ScheduleSubHeader style={{ fontSize: '12px', marginTop: '16px' }}>Backtesting</ScheduleSubHeader>
                    <ScheduleHint>Wednesday mid-week avoids Monday/Friday anomalies.</ScheduleHint>
                    <Field>
                      <Label>Backtest days</Label>
                      <ChipContainer>
                        {DAY_NAMES.map(d => (
                          <DayChip
                            key={`bt-${d}`}
                            type="button"
                            $active={backtestTradingDays.includes(d)}
                            onClick={() => toggleBacktestDay(d)}
                            disabled={isLoading}
                          >
                            {DAY_LABELS[d]}
                          </DayChip>
                        ))}
                      </ChipContainer>
                    </Field>

                    <Field>
                      <Label>Backtest times</Label>
                      <TickerInputRow>
                        <TickerInput
                          type="text"
                          value={backtestTimeInput}
                          onChange={e => setBacktestTimeInput(e.target.value)}
                          onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addBacktestTime(); } }}
                          placeholder="e.g., 12:00"
                          disabled={isLoading}
                        />
                        <TerminalButton
                          type="button"
                          variant="secondary"
                          onClick={addBacktestTime}
                          disabled={isLoading || !backtestTimeInput.trim()}
                        >
                          Add
                        </TerminalButton>
                      </TickerInputRow>
                      {backtestEvaluationTimes.length > 0 && (
                        <ChipContainer>
                          {backtestEvaluationTimes.map(t => (
                            <Chip key={`bt-${t}`}>
                              {t}
                              <ChipRemove type="button" onClick={() => removeBacktestTime(t)}>&times;</ChipRemove>
                            </Chip>
                          ))}
                        </ChipContainer>
                      )}
                    </Field>
                  </>
                )}
              </>
            )}

            {/* Expert mode: Risk Controls */}
            {expertMode && (
              <>
                <ScheduleSubHeader style={{ marginTop: '24px' }}>Risk Controls</ScheduleSubHeader>
                <ScheduleHint>
                  Set guardrails to protect your portfolio from overexposure.
                </ScheduleHint>

                <Field>
                  <Label>Cash reserve</Label>
                  <SliderRow>
                    <SliderLabel>Reserve</SliderLabel>
                    <SliderInput
                      type="range"
                      min={0}
                      max={50}
                      step={5}
                      value={reserveCash}
                      onChange={e => setReserveCash(Number(e.target.value))}
                      disabled={isLoading}
                    />
                    <SliderValue>{reserveCash}%</SliderValue>
                  </SliderRow>
                  <HelperText>Minimum cash that stays uninvested at all times.</HelperText>
                </Field>

                {tickers.length > 0 && (
                  <Field>
                    <Label>Position limits</Label>
                    {tickers.map(t => (
                      <SliderRow key={t}>
                        <SliderLabel>{t}</SliderLabel>
                        <SliderInput
                          type="range"
                          min={0}
                          max={100}
                          step={5}
                          value={positionLimits[t] ?? 25}
                          onChange={e => handlePositionLimit(t, Number(e.target.value))}
                          disabled={isLoading}
                        />
                        <SliderValue>{positionLimits[t] ?? 25}%</SliderValue>
                      </SliderRow>
                    ))}
                    <HelperText>Maximum percentage of your portfolio per asset.</HelperText>
                  </Field>
                )}
              </>
            )}

            {/* Agent Selection — always shown */}
            <ScheduleSubHeader style={{ marginTop: '24px' }}>AI Agents</ScheduleSubHeader>
            <ScheduleHint>
              Choose which AI agents analyze the market for you. Each agent brings a different perspective.
            </ScheduleHint>

            <Field>
              {AGENT_INFO.map(a => {
                const isActive = agents.includes(a.role);
                return (
                  <AgentCard key={a.role} $active={isActive}>
                    <AgentCardHeader onClick={() => !isLoading && toggleAgent(a.role)}>
                      <AgentCheckbox $checked={isActive}>{isActive ? '\u2713' : ''}</AgentCheckbox>
                      <ToggleLabel>
                        <ToggleTitle>{a.title}</ToggleTitle>
                        <ToggleDescription>{a.desc}</ToggleDescription>
                      </ToggleLabel>
                    </AgentCardHeader>
                  </AgentCard>
                );
              })}
            </Field>

            {/* Expert mode: Model overrides + full AI Configuration */}
            {expertMode && (
              <>
                <ScheduleSubHeader style={{ marginTop: '24px' }}>Model Configuration</ScheduleSubHeader>
                <ScheduleHint>
                  Pin agents to specific AI models. Leave empty for account defaults.
                </ScheduleHint>

                <Field>
                  <Label>AI model (all agents)</Label>
                  <ModelAutocomplete
                    value={llmModel}
                    onChange={setLlmModel}
                    placeholder="e.g., google/gemini-2.5-flash"
                    disabled={isLoading}
                    models={availableModels}
                  />
                  <HelperText>Pin all agents to a specific model. Leave empty for account defaults.</HelperText>
                </Field>

                <Field>
                  <Label>Per-agent model overrides</Label>
                  {AGENT_INFO.map(a => {
                    const isActive = agents.includes(a.role);
                    if (!isActive) return null;
                    const hasStrategyOverride = !!(llmModels[a.role]?.trim());
                    const isExpanded = expandedOverrides.has(a.role);

                    let resolvedModel: string;
                    let resolvedSource: string;
                    if (hasStrategyOverride) {
                      resolvedModel = llmModels[a.role];
                      resolvedSource = 'strategy override';
                    } else if (llmModel.trim()) {
                      resolvedModel = llmModel;
                      resolvedSource = 'strategy global';
                    } else if (accountHasOverride) {
                      resolvedModel = accountDefault!;
                      resolvedSource = 'account';
                    } else {
                      resolvedModel = systemDefault ?? '—';
                      resolvedSource = 'system default';
                    }

                    return (
                      <AgentCard key={a.role} $active={true}>
                        <AgentCardHeader style={{ cursor: 'default' }}>
                          <ToggleLabel>
                            <ToggleTitle>{a.title}</ToggleTitle>
                          </ToggleLabel>
                        </AgentCardHeader>

                        <AgentModelLine>
                          <AgentModelName $override={hasStrategyOverride}>{resolvedModel}</AgentModelName>
                          <span>({resolvedSource})</span>
                          <span>&middot;</span>
                          {hasStrategyOverride ? (
                            <AgentModelAction type="button" onClick={() => {
                              handleAgentModel(a.role, '');
                              setExpandedOverrides(prev => { const n = new Set(prev); n.delete(a.role); return n; });
                            }}>
                              reset
                            </AgentModelAction>
                          ) : (
                            <AgentModelAction type="button" onClick={() => {
                              setExpandedOverrides(prev => new Set(prev).add(a.role));
                            }}>
                              override
                            </AgentModelAction>
                          )}
                        </AgentModelLine>

                        {(isExpanded || hasStrategyOverride) && (
                          <AgentOverrideInput>
                            <ModelAutocomplete
                              value={llmModels[a.role] ?? ''}
                              onChange={v => handleAgentModel(a.role, v)}
                              placeholder="Search models..."
                              disabled={isLoading}
                              models={availableModels}
                            />
                          </AgentOverrideInput>
                        )}
                      </AgentCard>
                    );
                  })}

                  {consensusMethod === 'ORCHESTRATOR_OVERRIDE' && (() => {
                    const hasStrategyOverride = !!(llmModels['ORCHESTRATOR']?.trim());
                    const isExpanded = expandedOverrides.has('ORCHESTRATOR');

                    let resolvedModel: string;
                    let resolvedSource: string;
                    if (hasStrategyOverride) {
                      resolvedModel = llmModels['ORCHESTRATOR'];
                      resolvedSource = 'strategy override';
                    } else if (llmModel.trim()) {
                      resolvedModel = llmModel;
                      resolvedSource = 'strategy global';
                    } else if (accountHasOverride) {
                      resolvedModel = accountDefault!;
                      resolvedSource = 'account';
                    } else {
                      resolvedModel = systemDefault ?? '—';
                      resolvedSource = 'system default';
                    }

                    return (
                      <AgentCard $active={true}>
                        <AgentCardHeader style={{ cursor: 'default' }}>
                          <AgentCheckbox $checked={true} style={{ opacity: 0.5 }}>{'\u2713'}</AgentCheckbox>
                          <ToggleLabel>
                            <ToggleTitle>Orchestrator</ToggleTitle>
                            <ToggleDescription>Final decision maker (always active)</ToggleDescription>
                          </ToggleLabel>
                        </AgentCardHeader>

                        <AgentModelLine>
                          <AgentModelName $override={hasStrategyOverride}>{resolvedModel}</AgentModelName>
                          <span>({resolvedSource})</span>
                          <span>&middot;</span>
                          {hasStrategyOverride ? (
                            <AgentModelAction type="button" onClick={() => {
                              handleAgentModel('ORCHESTRATOR', '');
                              setExpandedOverrides(prev => { const n = new Set(prev); n.delete('ORCHESTRATOR'); return n; });
                            }}>
                              reset
                            </AgentModelAction>
                          ) : (
                            <AgentModelAction type="button" onClick={() => {
                              setExpandedOverrides(prev => new Set(prev).add('ORCHESTRATOR'));
                            }}>
                              override
                            </AgentModelAction>
                          )}
                        </AgentModelLine>

                        {(isExpanded || hasStrategyOverride) && (
                          <AgentOverrideInput>
                            <ModelAutocomplete
                              value={llmModels['ORCHESTRATOR'] ?? ''}
                              onChange={v => handleAgentModel('ORCHESTRATOR', v)}
                              placeholder="Search models..."
                              disabled={isLoading}
                              models={availableModels}
                            />
                          </AgentOverrideInput>
                        )}
                      </AgentCard>
                    );
                  })()}
                </Field>

                {consensusMethod === 'MAJORITY' && (
                  <Field>
                    <Label>Consensus threshold</Label>
                    <SliderRow>
                      <SliderLabel>Threshold</SliderLabel>
                      <SliderInput
                        type="range"
                        min={0}
                        max={100}
                        step={5}
                        value={consensusThreshold}
                        onChange={e => setConsensusThreshold(Number(e.target.value))}
                        disabled={isLoading}
                      />
                      <SliderValue>{consensusThreshold}%</SliderValue>
                    </SliderRow>
                    <HelperText>Agents unsure below this threshold abstain from voting.</HelperText>
                  </Field>
                )}

                <Field>
                  <Label>Position cooldown</Label>
                  <Input
                    type="text"
                    value={positionCooldown}
                    onChange={e => setPositionCooldown(e.target.value)}
                    placeholder="e.g., 14d, 2w"
                    disabled={isLoading}
                  />
                  <HelperText>
                    Skip evaluations for a ticker after buying or selling, to prevent rapid trades.
                    Format: number + unit (s, m, h, d, w). Examples: 30s, 5m, 1h, 14d, 2w.
                  </HelperText>
                </Field>
              </>
            )}
          </FieldGroup>
        );

      // --- Step 2: Instructions ---
      case 2:
        return (
          <FieldGroup>
            <Field>
              <Label>Agent Instructions</Label>
              <HelperText>
                This is the strategy your AI agents will execute on every trading evaluation.
                Describe when to buy, sell, or hold. Not sure where to start?{' '}
                <a href="/faq/strategies" target="_blank" rel="noopener noreferrer" style={{ color: 'inherit', textDecoration: 'underline' }}>
                  See examples and tips
                </a>{' '}
                or{' '}
                <a href="/strategies/public" target="_blank" rel="noopener noreferrer" style={{ color: 'inherit', textDecoration: 'underline' }}>
                  browse the strategy library
                </a>.
              </HelperText>
            </Field>

            <AgentContextInfo>
              <AgentContextTitle>What each agent sees:</AgentContextTitle>
              <AgentContextList>
                <li><strong>All agents:</strong> Portfolio state, current prices, recent trades, technical indicators, your strategy text</li>
                <li><strong>Technical:</strong> Multi-timeframe price statistics (24h/7d/30d/90d), volatility levels, chart patterns</li>
                <li><strong>Fundamental:</strong> Recent news headlines, market trajectory across timeframes, price trend analysis</li>
                <li><strong>Sentiment:</strong> Social media posts (Reddit), news sentiment scores, sentiment evolution over time</li>
                <li><strong>Orchestrator:</strong> All agent recommendations with confidence scores, trade history, minimum trade sizes</li>
              </AgentContextList>
            </AgentContextInfo>

            <Field>
              <Textarea
                value={markdownBody}
                onChange={e => setMarkdownBody(e.target.value)}
                placeholder={"Describe your trading strategy in plain language. For example:\n\n## Core Principles\n- Risk management first: never risk more than what the portfolio can withstand\n- In uncertain conditions, HOLD is a valid decision\n\n## BUY Criteria\n- At least 2 agents recommend BUY with high confidence\n- Technical indicators confirm upward momentum\n\n## SELL Criteria\n- At least 2 agents recommend SELL\n- OR sentiment turns strongly negative"}
                disabled={isLoading}
                rows={15}
              />
              <HelperText>
                Use plain language. Markdown formatting (headings, bullets, bold) is supported.
              </HelperText>
            </Field>
          </FieldGroup>
        );

      // --- Step 3: Review ---
      case 3:
        return (
          <FieldGroup>
            {/* Assets summary */}
            <ReviewCard>
              <ReviewCardHeader>
                <ReviewCardTitle>Assets</ReviewCardTitle>
                <ReviewEditLink type="button" onClick={() => goToStep(0)}>Edit</ReviewEditLink>
              </ReviewCardHeader>
              <ReviewRow>
                <ReviewRowLabel>Name</ReviewRowLabel>
                <ReviewRowValue style={!name.trim() ? { opacity: 0.5, fontStyle: 'italic' } : undefined}>
                  {name.trim() || generateName()}
                </ReviewRowValue>
              </ReviewRow>
              <ReviewRow>
                <ReviewRowLabel>Type</ReviewRowLabel>
                <ReviewRowValue>{assetType}</ReviewRowValue>
              </ReviewRow>
              <ReviewRow>
                <ReviewRowLabel>Tickers</ReviewRowLabel>
                <ReviewRowValue>{tickers.join(', ')}</ReviewRowValue>
              </ReviewRow>
            </ReviewCard>

            {/* Decision / Configuration summary */}
            <ReviewCard>
              <ReviewCardHeader>
                <ReviewCardTitle>{expertMode ? 'Configuration' : 'Decision Method'}</ReviewCardTitle>
                <ReviewEditLink type="button" onClick={() => goToStep(1)}>Edit</ReviewEditLink>
              </ReviewCardHeader>
              <ReviewRow>
                <ReviewRowLabel>Consensus</ReviewRowLabel>
                <ReviewRowValue>
                  {consensusMethod === 'MAJORITY' ? 'Majority Vote' : 'AI Orchestrator'}
                </ReviewRowValue>
              </ReviewRow>

              {expertMode && (
                <>
                  <ReviewRow>
                    <ReviewRowLabel>Live schedule</ReviewRowLabel>
                    <ReviewRowValue>
                      {assetType === 'CRYPTO'
                        ? CRYPTO_INTERVALS.find(i => i.value === cryptoInterval)?.label ?? cryptoInterval
                        : `${tradingDays.map(d => DAY_LABELS[d]).join(', ')} at ${evaluationTimes.join(', ')}`
                      }
                    </ReviewRowValue>
                  </ReviewRow>
                  <ReviewRow>
                    <ReviewRowLabel>Backtest</ReviewRowLabel>
                    <ReviewRowValue>
                      {assetType === 'CRYPTO'
                        ? BACKTEST_CRYPTO_INTERVALS.find(i => i.value === backtestCryptoInterval)?.label ?? backtestCryptoInterval
                        : `${backtestTradingDays.map(d => DAY_LABELS[d]).join(', ')} at ${backtestEvaluationTimes.join(', ')}`
                      }
                    </ReviewRowValue>
                  </ReviewRow>
                  <ReviewRow>
                    <ReviewRowLabel>Cash reserve</ReviewRowLabel>
                    <ReviewRowValue>{reserveCash}%</ReviewRowValue>
                  </ReviewRow>
                  {tickers.length > 0 && (
                    <ReviewRow>
                      <ReviewRowLabel>Position limits</ReviewRowLabel>
                      <ReviewRowValue>
                        {tickers.map(t => `${t}: ${positionLimits[t] ?? 25}%`).join(', ')}
                      </ReviewRowValue>
                    </ReviewRow>
                  )}
                  {agents.length < ALL_AGENTS.length && (
                    <ReviewRow>
                      <ReviewRowLabel>Agents</ReviewRowLabel>
                      <ReviewRowValue>
                        {agents.map(a => AGENT_INFO.find(i => i.role === a)?.title ?? a).join(', ')}
                      </ReviewRowValue>
                    </ReviewRow>
                  )}
                  {llmModel.trim() && (
                    <ReviewRow>
                      <ReviewRowLabel>Model</ReviewRowLabel>
                      <ReviewRowValue>{llmModel}</ReviewRowValue>
                    </ReviewRow>
                  )}
                  {consensusMethod === 'MAJORITY' && consensusThreshold !== 60 && (
                    <ReviewRow>
                      <ReviewRowLabel>Threshold</ReviewRowLabel>
                      <ReviewRowValue>{consensusThreshold}%</ReviewRowValue>
                    </ReviewRow>
                  )}
                  {positionCooldown.trim() && (
                    <ReviewRow>
                      <ReviewRowLabel>Cooldown</ReviewRowLabel>
                      <ReviewRowValue>{positionCooldown}</ReviewRowValue>
                    </ReviewRow>
                  )}
                </>
              )}
            </ReviewCard>

            {/* Instructions summary */}
            <ReviewCard>
              <ReviewCardHeader>
                <ReviewCardTitle>Instructions</ReviewCardTitle>
                <ReviewEditLink type="button" onClick={() => goToStep(2)}>Edit</ReviewEditLink>
              </ReviewCardHeader>
              <InstructionsPreview>
                {markdownBody.length > 200
                  ? markdownBody.slice(0, 200).trim() + '...'
                  : markdownBody}
              </InstructionsPreview>
            </ReviewCard>

            {/* Collapsible YAML preview */}
            <YamlToggle type="button" onClick={() => setShowYaml(!showYaml)}>
              {showYaml ? '- Hide' : '+ View'} generated YAML
            </YamlToggle>
            {showYaml && generatedYaml && (
              <YamlPreview>{generatedYaml}</YamlPreview>
            )}
          </FieldGroup>
        );

      default:
        return null;
    }
  };

  // =========================================================================
  // Render: Sidebar
  // =========================================================================

  const renderSidebar = () => {
    switch (currentStep) {
      case 0:
        return (
          <>
            <SidebarTitle>Choose your trading universe</SidebarTitle>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Name your strategy clearly &mdash; you&apos;ll see it in dashboards and reports
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Crypto trades 24/7 with interval-based evaluation
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Stocks trade on scheduled days/times during market hours
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Start with 2&ndash;3 tickers to keep things manageable
            </SidebarTip>
          </>
        );

      case 1:
        if (expertMode) {
          return (
            <>
              <SidebarTitle>Fine-tune your strategy</SidebarTitle>
              <SidebarTip>
                <SidebarTipIcon>&bull;</SidebarTipIcon>
                Shorter backtest intervals = more AI calls = higher cost
              </SidebarTip>
              <SidebarTip>
                <SidebarTipIcon>&bull;</SidebarTipIcon>
                Cash reserves protect against being fully invested during drops
              </SidebarTip>
              <SidebarTip>
                <SidebarTipIcon>&bull;</SidebarTipIcon>
                Position limits prevent overexposure to any single asset
              </SidebarTip>
              <SidebarTip>
                <SidebarTipIcon>&bull;</SidebarTipIcon>
                Each agent can use a different LLM model for specialized analysis
              </SidebarTip>
            </>
          );
        }
        return (
          <>
            <SidebarTitle>How decisions are made</SidebarTitle>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              <span><strong>Majority Vote:</strong> Each agent votes, majority wins. Simple and transparent.</span>
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              <span><strong>AI Orchestrator:</strong> A senior AI reviews all agents and makes the final call. Better for nuanced strategies.</span>
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Enable Expert mode to customize schedule, risk controls, and AI models
            </SidebarTip>
          </>
        );

      case 2:
        return (
          <>
            <SidebarTitle>Guide the AI agents</SidebarTitle>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Every agent and orchestrator reads these instructions before deciding
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Be concise &mdash; long instructions confuse simpler models
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Define clear BUY/SELL/HOLD criteria with specific thresholds
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              The default template works well for most strategies
            </SidebarTip>
          </>
        );

      case 3:
        return (
          <>
            <SidebarTitle>Almost done</SidebarTitle>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Review your settings before creating
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Everything can be edited after creation
            </SidebarTip>
            <SidebarTip>
              <SidebarTipIcon>&bull;</SidebarTipIcon>
              Click &ldquo;Edit&rdquo; on any section to jump back and make changes
            </SidebarTip>
          </>
        );

      default:
        return null;
    }
  };

  // =========================================================================
  // Render
  // =========================================================================

  return (
    <DashboardLayout>
      <PageContainer>
        <PageHeader>
          <Crumb>
            <Link href="/strategies">Strategies</Link>
            <CrumbSep>/</CrumbSep>
            <CrumbCurrent>New</CrumbCurrent>
          </Crumb>
          <PageTitle>Create strategy</PageTitle>
          <Subtitle>
            Build your trading strategy step by step. The AI council reads these settings on every tick — write for an analyst who has thirty seconds to understand your hypothesis.
          </Subtitle>
        </PageHeader>

        <FormCard>
          {/* ---- Stepper Bar ---- */}
          <StepperBar>
            <StepperTrack>
              {stepLabels.map((label, i) => (
                <Fragment key={i}>
                  {i > 0 && <StepConnector $completed={i <= currentStep} />}
                  <StepGroup
                    $clickable={i < currentStep}
                    onClick={() => i < currentStep && goToStep(i)}
                  >
                    <StepNode $state={getStepState(i)}>
                      {i < currentStep ? '\u2713' : i + 1}
                    </StepNode>
                    <StepLabelText $state={getStepState(i)}>{label}</StepLabelText>
                  </StepGroup>
                </Fragment>
              ))}
            </StepperTrack>

            <ExpertToggleInline $active={expertMode}>
              <ToggleInput
                type="checkbox"
                checked={expertMode}
                onChange={() => setExpertMode(!expertMode)}
              />
              <ToggleSwitchSmall $checked={expertMode} />
              <ExpertLabel $active={expertMode}>Expert</ExpertLabel>
            </ExpertToggleInline>
          </StepperBar>

          {/* ---- Mobile Tips Toggle ---- */}
          <MobileTipsToggle type="button" onClick={() => setShowMobileTips(!showMobileTips)}>
            {showMobileTips ? '\u25BE Hide tips' : '\u25B8 Show tips'}
          </MobileTipsToggle>

          {/* ---- Wizard Body ---- */}
          <WizardBody>
            <WizardSidebar $mobileExpanded={showMobileTips}>
              {renderSidebar()}
            </WizardSidebar>

            <StepContentArea key={`step-${currentStep}`} $direction={direction}>
              {renderStepContent()}
            </StepContentArea>
          </WizardBody>

          {/* ---- Navigation Bar ---- */}
          <NavigationBar>
            {currentStep === 0 ? (
              <TerminalButton
                type="button"
                variant="ghost"
                onClick={handleCancel}
                disabled={isLoading}
              >
                Cancel
              </TerminalButton>
            ) : (
              <TerminalButton
                type="button"
                variant="ghost"
                onClick={goBack}
                disabled={isLoading}
              >
                &larr; Back
              </TerminalButton>
            )}

            <StepIndicator>
              Step {currentStep + 1} of {TOTAL_STEPS}
            </StepIndicator>

            {currentStep === TOTAL_STEPS - 1 ? (
              <TerminalButton
                type="button"
                onClick={handleSave}
                disabled={isLoading || !assetType || tickers.length === 0}
              >
                {isLoading ? 'Creating...' : 'Create Strategy'}
              </TerminalButton>
            ) : (
              <TerminalButton
                type="button"
                onClick={goNext}
                disabled={!canContinue || isLoading}
              >
                Continue &rarr;
              </TerminalButton>
            )}
          </NavigationBar>
        </FormCard>
      </PageContainer>
    </DashboardLayout>
  );
}
