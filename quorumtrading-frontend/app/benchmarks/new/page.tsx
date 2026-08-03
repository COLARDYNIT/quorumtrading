'use client';

import { useState, useEffect, FormEvent, Suspense } from 'react';
import styled from 'styled-components';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { TerminalInput } from '@/app/components/terminal/TerminalInput';
import { TerminalButton } from '@/app/components/terminal';
import { useStrategies, useCreateBenchmark, useBrokerAccounts } from '@/app/lib/api/hooks';
import { CreateBenchmarkRequest } from '@/app/lib/api/client';
import { fadeInUp } from '@/app/styles/animations';
import type { CostPreset } from '@/app/lib/types/brokerAccount';
import { getCostPresetDescription, formatMoney } from '@/app/lib/types/brokerAccount';
import { PeriodSelector } from '@/app/components/backtest/PeriodSelector';
import { BacktestDurationWarning } from '@/app/components/backtest/BacktestDurationWarning';

const WizardContainer = styled.div`
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

const PageDescription = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const WizardForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const Section = styled.section`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
`;

const SectionTitle = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
  margin: 0 0 2px 0;
  letter-spacing: 0;
`;

const SectionDescription = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.5;
  margin: 0 0 6px 0;
`;

const StrategiesList = styled.div`
  display: flex;
  flex-direction: column;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
  max-height: 400px;
  overflow-y: auto;

  /* Custom scrollbar */
  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
  }

  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.foreground}40;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: ${p => p.theme.colors.foreground}60;
  }
`;

const StrategiesHeader = styled.div`
  display: grid;
  grid-template-columns: 32px 1fr auto 70px;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const StrategyRow = styled.label<{ $selected: boolean }>`
  display: grid;
  grid-template-columns: 32px 1fr auto 70px;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  cursor: pointer;
  transition: background 0.15s ease;
  align-items: center;
  background: ${p => p.$selected ? `${p.theme.colors.primary}0a` : 'transparent'};

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: ${p => p.theme.colors.primary}0a;
  }
`;

const Checkbox = styled.input.attrs({ type: 'checkbox' })`
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: ${p => p.theme.colors.primary};
`;

const StrategyName = styled.span<{ $selected: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.$selected ? p.theme.colors.primary : p.theme.colors.foreground};
  font-weight: ${p => p.$selected ? p.theme.typography.fontWeight.medium : 'normal'};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const StrategyTypeBadge = styled.span<{ $type: 'crypto' | 'stock' | 'mixed' }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: ${p => {
    if (p.$type === 'crypto') return `${p.theme.colors.terminalAmber}22`;
    if (p.$type === 'stock') return `${p.theme.colors.info}22`;
    return `${p.theme.colors.terminalPink}22`;
  }};
  color: ${p => {
    if (p.$type === 'crypto') return p.theme.colors.terminalAmber;
    if (p.$type === 'stock') return p.theme.colors.info;
    return p.theme.colors.terminalPink;
  }};
  border: 1px solid ${p => {
    if (p.$type === 'crypto') return `${p.theme.colors.terminalAmber}44`;
    if (p.$type === 'stock') return `${p.theme.colors.info}44`;
    return `${p.theme.colors.terminalPink}44`;
  }};
  padding: 2px 8px;
  font-size: 11px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  text-transform: uppercase;
  border-radius: ${p => p.theme.borderRadius.sm};
  letter-spacing: 0.03em;
`;

const TickerBadges = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
`;

const TickerBadge = styled.span<{ $type: 'crypto' | 'stock' }>`
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  font-size: 11px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  background: ${p => p.$type === 'crypto' ? `${p.theme.colors.terminalAmber}15` : `${p.theme.colors.info}15`};
  color: ${p => p.$type === 'crypto' ? p.theme.colors.terminalAmber : p.theme.colors.info};
  border-radius: 2px;
`;

const BulkActions = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.md};
  margin-bottom: ${p => p.theme.spacing.md};
`;

const ModeToggle = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  margin-bottom: ${p => p.theme.spacing.lg};
`;

const ModeButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})<{ active: boolean }>`
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.md};
  border: 1px solid ${p => p.active ? p.theme.colors.primary : p.theme.colors.border};
  background: ${p => p.active ? p.theme.colors.primary + '20' : p.theme.colors.surface};
  color: ${p => p.active ? p.theme.colors.primary : p.theme.colors.foreground};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  cursor: pointer;
  transition: all 0.2s;
  text-transform: uppercase;
  letter-spacing: 0.05em;

  &:hover {
    border-color: ${p => p.theme.colors.primary};
  }
`;

const FormActions = styled.div`
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 4px 0;
`;

const ErrorMessage = styled.div`
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  padding: ${p => p.theme.spacing.md};
  border: 1px solid ${p => p.theme.colors.error};
  background: ${p => p.theme.colors.error}10;
`;

const LoadingMessage = styled.div`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  text-align: center;
  padding: ${p => p.theme.spacing.xl};
`;

// Broker configuration styled components
const BrokerTypeToggle = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  margin-bottom: ${p => p.theme.spacing.lg};
`;

const BrokerTypeButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active' && prop !== 'variant',
})<{ active: boolean; variant?: 'default' | 'warning' }>`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border: 1px solid ${p => {
    if (p.active) {
      return p.variant === 'warning' ? p.theme.colors.terminalAmber : p.theme.colors.primary;
    }
    return p.theme.colors.border;
  }};
  background: ${p => {
    if (p.active) {
      return p.variant === 'warning' ? p.theme.colors.terminalAmber + '20' : p.theme.colors.primary + '20';
    }
    return p.theme.colors.surface;
  }};
  color: ${p => {
    if (p.active) {
      return p.variant === 'warning' ? p.theme.colors.terminalAmber : p.theme.colors.primary;
    }
    return p.theme.colors.foreground;
  }};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  cursor: pointer;
  transition: all 0.2s;
  text-transform: uppercase;
  letter-spacing: 0.05em;

  &:hover {
    border-color: ${p => p.variant === 'warning' ? p.theme.colors.terminalAmber : p.theme.colors.primary};
  }
`;

const MockConfigContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
`;

const CostPresetGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: ${p => p.theme.spacing.sm};

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
`;

const CostPresetOption = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})<{ active: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.md};
  background: ${p => p.active ? `${p.theme.colors.terminalGreen}20` : p.theme.colors.surface};
  border: 1px solid ${p => p.active ? p.theme.colors.terminalGreen : p.theme.colors.border};
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${p => p.theme.colors.terminalGreen};
  }
`;

const CostPresetLabel = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})<{ active: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.active ? p.theme.colors.terminalGreen : p.theme.colors.text};
`;

const CostPresetDescription = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const AccountsList = styled.div`
  display: flex;
  flex-direction: column;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
`;

const AccountsHeader = styled.div`
  display: grid;
  grid-template-columns: 32px 1fr auto;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const AccountRow = styled.label<{ $selected: boolean }>`
  display: grid;
  grid-template-columns: 32px 1fr auto;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  cursor: pointer;
  transition: background 0.15s ease;
  align-items: center;
  background: ${p => p.$selected ? `${p.theme.colors.primary}0a` : 'transparent'};

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: ${p => p.theme.colors.primary}0a;
  }
`;

const RadioButton = styled.input.attrs({ type: 'radio' })`
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: ${p => p.theme.colors.primary};
`;

const AccountName = styled.span<{ $selected: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.$selected ? p.theme.colors.primary : p.theme.colors.foreground};
  font-weight: ${p => p.$selected ? p.theme.typography.fontWeight.medium : 'normal'};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const AccountCapital = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.terminalGreen};
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const FormLabel = styled.label`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const NoAccountsMessage = styled.div`
  padding: ${p => p.theme.spacing.md};
  border: 1px dashed ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  text-align: center;
`;

const InfoNote = styled.div`
  margin-top: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.md};
  border: 1px solid ${p => p.theme.colors.terminalBlue}40;
  border-left: 3px solid ${p => p.theme.colors.terminalBlue};
  background: ${p => p.theme.colors.terminalBlue}08;
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  line-height: 1.6;

  a {
    color: ${p => p.theme.colors.terminalBlue};
    text-decoration: underline;
  }
`;

// Live trading risk warning styled components
const RiskWarningBanner = styled.div`
  margin-top: ${p => p.theme.spacing.lg};
  padding: ${p => p.theme.spacing.lg};
  border: 2px solid ${p => p.theme.colors.terminalAmber};
  background: ${p => p.theme.colors.terminalAmber}10;
  animation: ${fadeInUp} 0.3s ease-out;
`;

const RiskWarningHeader = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  margin-bottom: ${p => p.theme.spacing.md};
  padding-bottom: ${p => p.theme.spacing.sm};
  border-bottom: 1px solid ${p => p.theme.colors.terminalAmber}40;
`;

const RiskWarningIcon = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xl};
`;

const RiskWarningTitle = styled.h3`
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.lg};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalAmber};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const RiskWarningContent = styled.div`
  color: ${p => p.theme.colors.foreground};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  line-height: 1.6;
`;

const RiskWarningIntro = styled.p`
  margin: 0 0 ${p => p.theme.spacing.md} 0;
  color: ${p => p.theme.colors.foreground};
`;

const RiskList = styled.ul`
  margin: 0 0 ${p => p.theme.spacing.md} 0;
  padding-left: ${p => p.theme.spacing.lg};
  list-style: none;

  li {
    position: relative;
    margin-bottom: ${p => p.theme.spacing.xs};
    padding-left: ${p => p.theme.spacing.sm};

    &::before {
      content: '\u2022';
      position: absolute;
      left: -${p => p.theme.spacing.md};
      color: ${p => p.theme.colors.terminalAmber};
    }
  }
`;

const RiskDisclaimer = styled.p`
  margin: 0 0 ${p => p.theme.spacing.lg} 0;
  color: ${p => p.theme.colors.textMuted};
  font-size: ${p => p.theme.typography.fontSize.xs};
  line-height: 1.6;
`;

const RiskAcknowledgmentLabel = styled.label`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  cursor: pointer;
  padding: ${p => p.theme.spacing.sm};
  border: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.surface};
  transition: all 0.2s ease;

  &:hover {
    border-color: ${p => p.theme.colors.terminalAmber};
    background: ${p => p.theme.colors.terminalAmber}08;
  }
`;

const RiskAcknowledgmentCheckbox = styled.input.attrs({ type: 'checkbox' })`
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: ${p => p.theme.colors.terminalAmber};
`;

const RiskAcknowledgmentText = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.foreground};
`;

type Mode = 'backtest' | 'live';
type LiveBrokerType = 'mock' | 'paper' | 'real';
type StrategyType = 'crypto' | 'stock' | 'mixed';

interface ParsedTickers {
  crypto: string[];
  stock: string[];
  type: StrategyType;
}

// Helper to parse tickers from strategy markdown
function parseStrategyTickers(markdownContent: string): ParsedTickers {
  try {
    const yamlMatch = markdownContent.match(/^---\n([\s\S]*?)\n---/);
    if (!yamlMatch) {
      return { crypto: [], stock: [], type: 'crypto' };
    }

    const yamlContent = yamlMatch[1];

    // Parse crypto_tickers
    const cryptoMatch = yamlContent.match(/crypto_tickers:\s*\n((?:\s+-\s+\w+\s*(?:\n|$))*)/);
    const cryptoTickers = cryptoMatch
      ? cryptoMatch[1]
          .split('\n')
          .map(line => line.trim().replace(/^-\s+/, ''))
          .filter(ticker => ticker.length > 0)
      : [];

    // Parse stock_tickers
    const stockMatch = yamlContent.match(/stock_tickers:\s*\n((?:\s+-\s+\w+\s*(?:\n|$))*)/);
    const stockTickers = stockMatch
      ? stockMatch[1]
          .split('\n')
          .map(line => line.trim().replace(/^-\s+/, ''))
          .filter(ticker => ticker.length > 0)
      : [];

    // Determine type
    let type: StrategyType = 'crypto';
    if (cryptoTickers.length > 0 && stockTickers.length > 0) {
      type = 'mixed';
    } else if (stockTickers.length > 0) {
      type = 'stock';
    } else if (cryptoTickers.length > 0) {
      type = 'crypto';
    }

    return { crypto: cryptoTickers, stock: stockTickers, type };
  } catch {
    return { crypto: [], stock: [], type: 'crypto' };
  }
}

const COST_PRESETS: CostPreset[] = ['ZERO_FEES', 'TYPICAL_RETAIL', 'HIGH_FRICTION'];
const DEFAULT_INITIAL_CAPITAL = 100000;
const DEFAULT_CAPITAL_PER_STRATEGY = 10000;

// Tiingo provides daily stock data back to ~1993; earliest reliable date
const MIN_BACKTEST_DATE = '1993-01-29T00:00';

function WizardPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const preselectedStrategyId = searchParams.get('strategyId');
  const { data: strategies, isLoading: strategiesLoading, error: strategiesError } = useStrategies();
  const { data: brokerAccounts, isLoading: brokerAccountsLoading } = useBrokerAccounts();
  const createBenchmark = useCreateBenchmark();

  const [mode, setMode] = useState<Mode>('backtest');
  const [name, setName] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [validationError, setValidationError] = useState('');
  const [selectedStrategyIds, setSelectedStrategyIds] = useState<Set<string>>(new Set());
  const [initialized, setInitialized] = useState(false);

  // Broker configuration state
  const [liveBrokerType, setLiveBrokerType] = useState<LiveBrokerType>('mock');
  const [initialCapital, setInitialCapital] = useState(DEFAULT_INITIAL_CAPITAL);
  const [costPreset, setCostPreset] = useState<CostPreset>('TYPICAL_RETAIL');
  const [selectedBrokerAccountId, setSelectedBrokerAccountId] = useState<string | null>(null);
  const [capitalPerStrategy, setCapitalPerStrategy] = useState(DEFAULT_CAPITAL_PER_STRATEGY);
  const [riskAcknowledged, setRiskAcknowledged] = useState(false);

  // Filter broker accounts based on selected live broker type
  const paperAccounts = brokerAccounts?.filter(a => a.brokerType === 'ALPACA_PAPER') || [];
  const realAccounts = brokerAccounts?.filter(a => a.brokerType === 'ALPACA_LIVE') || [];
  const availableAccounts = liveBrokerType === 'paper' ? paperAccounts : liveBrokerType === 'real' ? realAccounts : [];

  // Get selected account details
  const selectedAccount = selectedBrokerAccountId
    ? brokerAccounts?.find(a => a.id === selectedBrokerAccountId)
    : null;

  // Initialize strategy selection when they load
  useEffect(() => {
    if (strategies && strategies.length > 0 && !initialized) {
      if (preselectedStrategyId) {
        // If a specific strategy is requested via URL, select only that one
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setSelectedStrategyIds(new Set([preselectedStrategyId]));
      } else {
        // Otherwise select all strategies
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setSelectedStrategyIds(new Set(strategies.map(s => s.id)));
      }
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setInitialized(true);
    }
  }, [strategies, preselectedStrategyId, initialized]);

  // Initialize default dates on mount: 48 hours ago until now, rounded down to nearest hour
  useEffect(() => {
    const now = new Date();

    // Round down to nearest hour
    const roundDownToHour = (date: Date) => {
      const rounded = new Date(date);
      rounded.setMinutes(0, 0, 0); // Set minutes, seconds, milliseconds to 0
      return rounded;
    };

    const endDate = roundDownToHour(now);
    const startDate = roundDownToHour(new Date(now.getTime() - 48 * 60 * 60 * 1000)); // 48 hours ago

    // Format for datetime-local input: YYYY-MM-DDTHH:MM
    const formatDatetimeLocal = (date: Date) => {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      return `${year}-${month}-${day}T${hours}:${minutes}`;
    };

    setStartDate(formatDatetimeLocal(startDate));
    setEndDate(formatDatetimeLocal(endDate));
  }, []); // Run only once on mount

  const handleToggleStrategy = (strategyId: string) => {
    const newSelected = new Set(selectedStrategyIds);
    if (newSelected.has(strategyId)) {
      newSelected.delete(strategyId);
    } else {
      newSelected.add(strategyId);
    }
    setSelectedStrategyIds(newSelected);
  };

  const handleSelectAll = () => {
    if (strategies) {
      setSelectedStrategyIds(new Set(strategies.map(s => s.id)));
    }
  };

  const handleDeselectAll = () => {
    setSelectedStrategyIds(new Set());
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setValidationError('');

    // Validation
    if (selectedStrategyIds.size === 0) {
      setValidationError('Please select at least one strategy');
      return;
    }

    if (mode === 'backtest') {
      if (!startDate) {
        setValidationError('Please provide a start date');
        return;
      }

      if (!endDate) {
        setValidationError('Please provide an end date for backtest mode');
        return;
      }

      if (new Date(endDate) <= new Date(startDate)) {
        setValidationError('End date must be after start date');
        return;
      }

      if (new Date(startDate) < new Date(MIN_BACKTEST_DATE)) {
        setValidationError(
          'Start date cannot be before January 29, 1993. ' +
          'Historical market data is not available before this date.'
        );
        return;
      }
    }

    // Live mode with real broker requires risk acknowledgment
    if (mode === 'live' && liveBrokerType === 'real' && !riskAcknowledged) {
      setValidationError('You must acknowledge the live trading risks before proceeding');
      return;
    }

    // Paper/Real mode requires selecting an account
    if (mode === 'live' && (liveBrokerType === 'paper' || liveBrokerType === 'real')) {
      if (!selectedBrokerAccountId) {
        setValidationError('Please select a broker account');
        return;
      }

      // Ensure capital per strategy is valid
      if (capitalPerStrategy <= 0) {
        setValidationError('Capital per strategy must be greater than zero');
        return;
      }

      // Ensure selected account exists (could be stale if account was deleted)
      if (!selectedAccount) {
        setValidationError('Selected broker account not found. Please refresh and try again.');
        return;
      }

      // Validate capital sufficiency
      const requiredCapital = capitalPerStrategy * selectedStrategyIds.size;
      if (selectedAccount.availableBalance.amount < requiredCapital) {
        const requiredMoney = { amount: requiredCapital, currency: selectedAccount.availableBalance.currency };
        setValidationError(
          `Insufficient funds. Need ${formatMoney(requiredMoney)} for ${selectedStrategyIds.size} strategies, ` +
          `but only ${formatMoney(selectedAccount.availableBalance)} available.`
        );
        return;
      }
    }

    // Convert datetime-local format to ISO 8601 UTC
    // datetime-local gives us: "2025-11-08T20:00"
    // Backend expects: "2025-11-08T20:00:00.000Z"
    // We append ":00.000Z" directly instead of using new Date().toISOString()
    // to avoid the browser's local timezone offset shifting the date backward
    // (e.g. CET midnight "2020-08-01T00:00" would become "2020-07-31T22:00:00Z")
    // For LIVE mode, don't send startDate - backend will use now()
    const startDateISO = mode === 'backtest' && startDate ? `${startDate}:00.000Z` : undefined;
    const endDateISO = mode === 'backtest' && endDate ? `${endDate}:00.000Z` : undefined;

    // Build request with broker configuration
    const request: CreateBenchmarkRequest = {
      strategyIds: Array.from(selectedStrategyIds),
      startDate: startDateISO,
      endDate: endDateISO,
      name: name.trim() || undefined,
    };

    // Add broker configuration based on mode and broker type
    if (mode === 'backtest' || liveBrokerType === 'mock') {
      // Mock broker: create new accounts with specified config
      request.mockConfig = {
        initialCapital,
        preset: costPreset,
      };
    } else if (selectedBrokerAccountId) {
      // Paper/Real: use existing broker account
      request.brokerAccountId = selectedBrokerAccountId;
      request.capitalPerStrategy = capitalPerStrategy;
    }

    try {
      const response = await createBenchmark.mutateAsync(request);
      if (response.benchmarkId) {
        // Multi-strategy: navigate to benchmark detail page
        router.push(`/benchmarks/${response.benchmarkId}`);
      } else {
        // Single strategy: navigate directly to simulation
        router.push(`/simulations/${response.simulationIds[0]}`);
      }
    } catch (error) {
      // Error handling is done by the mutation hook (toast notification)
      console.error('Failed to create simulation:', error);
    }
  };

  if (strategiesLoading) {
    return (
      <DashboardLayout>
        <WizardContainer>
          <LoadingMessage>Loading strategies...</LoadingMessage>
        </WizardContainer>
      </DashboardLayout>
    );
  }

  if (strategiesError) {
    return (
      <DashboardLayout>
        <WizardContainer>
          <ErrorMessage>
            Failed to load strategies: {strategiesError instanceof Error ? strategiesError.message : 'Unknown error'}
          </ErrorMessage>
        </WizardContainer>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <WizardContainer>
        <PageHeader>
          <Crumb>
            <Link href="/benchmarks">Benchmarks</Link>
            <CrumbSep>/</CrumbSep>
            <CrumbCurrent>New</CrumbCurrent>
          </Crumb>
          <PageTitle>{preselectedStrategyId ? 'Launch simulation' : 'Create benchmark'}</PageTitle>
          <PageDescription>
            {preselectedStrategyId
              ? 'Configure and run a backtest or live simulation for your strategy.'
              : 'Group 2–8 simulations to compare side-by-side. Most useful for version drift — same strategy at v8.3 vs v9.0 vs v9.1 over the same backtest window.'}
          </PageDescription>
        </PageHeader>

        <WizardForm onSubmit={handleSubmit}>
          {/* Strategy Selection */}
          <Section>
            <SectionTitle>1. Select Strategies</SectionTitle>
            <SectionDescription>
              Choose which trading strategies to include in this benchmark. All strategies are selected by default.
            </SectionDescription>

            <BulkActions>
              <TerminalButton variant="secondary" size="sm" type="button" onClick={handleSelectAll}>
                Select All
              </TerminalButton>
              <TerminalButton variant="secondary" size="sm" type="button" onClick={handleDeselectAll}>
                Deselect All
              </TerminalButton>
              <span style={{ color: 'var(--foreground)', fontSize: '0.875rem', alignSelf: 'center' }}>
                ({selectedStrategyIds.size} selected)
              </span>
            </BulkActions>

            <StrategiesList>
              <StrategiesHeader>
                <span></span>
                <span>Strategy</span>
                <span>Tickers</span>
                <span>Type</span>
              </StrategiesHeader>
              {strategies?.map(strategy => {
                const isSelected = selectedStrategyIds.has(strategy.id);
                const tickers = parseStrategyTickers(strategy.markdownContent || '');
                return (
                  <StrategyRow key={strategy.id} $selected={isSelected}>
                    <Checkbox
                      checked={isSelected}
                      onChange={() => handleToggleStrategy(strategy.id)}
                    />
                    <StrategyName $selected={isSelected}>{strategy.name}</StrategyName>
                    <TickerBadges>
                      {tickers.crypto.map(ticker => (
                        <TickerBadge key={ticker} $type="crypto">{ticker}</TickerBadge>
                      ))}
                      {tickers.stock.map(ticker => (
                        <TickerBadge key={ticker} $type="stock">{ticker}</TickerBadge>
                      ))}
                    </TickerBadges>
                    <StrategyTypeBadge $type={tickers.type}>
                      {tickers.type}
                    </StrategyTypeBadge>
                  </StrategyRow>
                );
              })}
            </StrategiesList>
          </Section>

          {/* Mode Selection */}
          <Section>
            <SectionTitle>2. Choose Mode</SectionTitle>
            <SectionDescription>
              Select backtest mode for historical data analysis or live mode for real-time trading.
            </SectionDescription>

            <ModeToggle>
              <ModeButton
                type="button"
                active={mode === 'backtest'}
                onClick={() => setMode('backtest')}
              >
                Backtest
              </ModeButton>
              <ModeButton
                type="button"
                active={mode === 'live'}
                onClick={() => setMode('live')}
              >
                Live
              </ModeButton>
            </ModeToggle>

            {mode === 'backtest' ? (
              <>
                <PeriodSelector
                  startDate={startDate}
                  endDate={endDate}
                  onStartDateChange={setStartDate}
                  onEndDateChange={setEndDate}
                />
                <BacktestDurationWarning
                  startDate={startDate}
                  endDate={endDate}
                  strategyIds={Array.from(selectedStrategyIds)}
                />
                {startDate && new Date(startDate) < new Date(MIN_BACKTEST_DATE) && (
                  <ErrorMessage>
                    Start date is before January 29, 1993. Historical market data is not available before this date.
                  </ErrorMessage>
                )}
              </>
            ) : (
              <SectionDescription style={{ marginTop: '1rem' }}>
                Live simulations start immediately from the current time.
              </SectionDescription>
            )}
          </Section>

          {/* Broker Configuration */}
          <Section>
            <SectionTitle>
              3. {mode === 'backtest' ? 'Configure Mock Account' : 'Configure Broker'}
            </SectionTitle>
            <SectionDescription>
              {mode === 'backtest'
                ? 'Configure the simulated trading account for backtesting.'
                : 'Select the broker type and configure your trading account.'
              }
            </SectionDescription>

            {/* Live mode: show broker type toggle */}
            {mode === 'live' && (
              <BrokerTypeToggle>
                <BrokerTypeButton
                  type="button"
                  active={liveBrokerType === 'mock'}
                  onClick={() => {
                    setLiveBrokerType('mock');
                    setSelectedBrokerAccountId(null);
                    setRiskAcknowledged(false);
                  }}
                >
                  Mock
                </BrokerTypeButton>
                <BrokerTypeButton
                  type="button"
                  active={liveBrokerType === 'paper'}
                  onClick={() => {
                    setLiveBrokerType('paper');
                    setSelectedBrokerAccountId(null);
                    setRiskAcknowledged(false);
                  }}
                >
                  Paper
                </BrokerTypeButton>
                <BrokerTypeButton
                  type="button"
                  active={liveBrokerType === 'real'}
                  variant="warning"
                  onClick={() => {
                    setLiveBrokerType('real');
                    setSelectedBrokerAccountId(null);
                  }}
                >
                  Real
                </BrokerTypeButton>
              </BrokerTypeToggle>
            )}

            {/* Mock configuration (backtest or live with mock) */}
            {(mode === 'backtest' || liveBrokerType === 'mock') && (
              <MockConfigContainer>
                <TerminalInput
                  type="number"
                  label="Initial Capital"
                  value={initialCapital.toString()}
                  onChange={(e) => setInitialCapital(parseFloat(e.target.value) || DEFAULT_INITIAL_CAPITAL)}
                  min={1000}
                  step={1000}
                  helperText="Starting capital in USD"
                />

                <FormGroup>
                  <FormLabel>Cost Preset</FormLabel>
                  <CostPresetGrid>
                    {COST_PRESETS.map(preset => (
                      <CostPresetOption
                        key={preset}
                        type="button"
                        active={costPreset === preset}
                        onClick={() => setCostPreset(preset)}
                      >
                        <CostPresetLabel active={costPreset === preset}>
                          {preset.replace(/_/g, ' ')}
                        </CostPresetLabel>
                        <CostPresetDescription>
                          {getCostPresetDescription(preset)}
                        </CostPresetDescription>
                      </CostPresetOption>
                    ))}
                  </CostPresetGrid>
                </FormGroup>

                {/* Info about paper/real accounts when in live mode and none connected */}
                {mode === 'live' && paperAccounts.length === 0 && realAccounts.length === 0 && (
                  <InfoNote>
                    Want to trade with real market data? You can{' '}
                    <Link href="/settings/broker-accounts">connect an Alpaca account</Link>{' '}
                    for paper or live trading.
                  </InfoNote>
                )}
              </MockConfigContainer>
            )}

            {/* Paper/Real account configuration */}
            {mode === 'live' && (liveBrokerType === 'paper' || liveBrokerType === 'real') && (
              <MockConfigContainer>
                {brokerAccountsLoading ? (
                  <LoadingMessage>Loading broker accounts...</LoadingMessage>
                ) : availableAccounts.length === 0 ? (
                  <NoAccountsMessage>
                    No {liveBrokerType === 'paper' ? 'paper' : 'live'} trading accounts found.
                    <br />
                    <Link href="/settings/broker-accounts" style={{ color: 'inherit', textDecoration: 'underline' }}>
                      Connect an account
                    </Link>{' '}in Settings first.
                  </NoAccountsMessage>
                ) : (
                  <>
                    <AccountsList>
                      <AccountsHeader>
                        <span></span>
                        <span>Account</span>
                        <span>Available</span>
                      </AccountsHeader>
                      {availableAccounts.map(account => {
                        const isSelected = selectedBrokerAccountId === account.id;
                        return (
                          <AccountRow key={account.id} $selected={isSelected}>
                            <RadioButton
                              name="brokerAccount"
                              checked={isSelected}
                              onChange={() => setSelectedBrokerAccountId(account.id)}
                            />
                            <AccountName $selected={isSelected}>{account.name}</AccountName>
                            <AccountCapital>{formatMoney(account.availableBalance)}</AccountCapital>
                          </AccountRow>
                        );
                      })}
                    </AccountsList>

                    <TerminalInput
                      type="number"
                      label="Capital Per Strategy"
                      value={capitalPerStrategy.toString()}
                      onChange={(e) => setCapitalPerStrategy(parseFloat(e.target.value) || DEFAULT_CAPITAL_PER_STRATEGY)}
                      min={100}
                      step={100}
                      helperText="Amount to allocate to each strategy"
                    />
                  </>
                )}
              </MockConfigContainer>
            )}

            {/* Live trading risk warning - only show for real broker type */}
            {mode === 'live' && liveBrokerType === 'real' && (
              <RiskWarningBanner>
                <RiskWarningHeader>
                  <RiskWarningIcon>!</RiskWarningIcon>
                  <RiskWarningTitle>Live Trading Warning</RiskWarningTitle>
                </RiskWarningHeader>
                <RiskWarningContent>
                  <RiskWarningIntro>
                    You are about to start live trading with real money.
                  </RiskWarningIntro>
                  <RiskList>
                    <li>AI agents will autonomously execute trades</li>
                    <li>Buy and sell orders will use actual funds</li>
                    <li>Losses are real and irreversible</li>
                  </RiskList>
                  <RiskDisclaimer>
                    QuorumTrading provides no financial advice and accepts no responsibility
                    for trading decisions made by AI agents. You are solely responsible for
                    any profits or losses incurred.
                  </RiskDisclaimer>
                  <RiskAcknowledgmentLabel>
                    <RiskAcknowledgmentCheckbox
                      checked={riskAcknowledged}
                      onChange={(e) => setRiskAcknowledged(e.target.checked)}
                    />
                    <RiskAcknowledgmentText>
                      I understand and accept these risks
                    </RiskAcknowledgmentText>
                  </RiskAcknowledgmentLabel>
                </RiskWarningContent>
              </RiskWarningBanner>
            )}
          </Section>

          {/* Optional Name */}
          <Section>
            <SectionTitle>4. Name (Optional)</SectionTitle>
            <SectionDescription>
              Provide a custom name for this benchmark. If left empty, a name will be auto-generated based on the mode and date range.
            </SectionDescription>

            <TerminalInput
              type="text"
              label="Benchmark Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., November 2025 Performance Test"
              helperText="Leave empty for auto-generated name"
            />
          </Section>

          {/* Validation Error */}
          {validationError && (
            <ErrorMessage>{validationError}</ErrorMessage>
          )}

          {/* Submit */}
          <FormActions>
            <TerminalButton
              variant="secondary"
              type="button"
              onClick={() => router.push('/')}
            >
              Cancel
            </TerminalButton>
            <TerminalButton
              variant="primary"
              type="submit"
              disabled={createBenchmark.isPending}
            >
              {createBenchmark.isPending ? 'Creating...' : 'Create Benchmark'}
            </TerminalButton>
          </FormActions>
        </WizardForm>
      </WizardContainer>
    </DashboardLayout>
  );
}

export default function WizardPage() {
  return (
    <Suspense fallback={
      <DashboardLayout>
        <WizardContainer>
          <LoadingMessage>Loading...</LoadingMessage>
        </WizardContainer>
      </DashboardLayout>
    }>
      <WizardPageContent />
    </Suspense>
  );
}
