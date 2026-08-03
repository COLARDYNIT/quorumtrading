/**
 * Register all A2UI catalog components. Call once at app initialization.
 */
import { registerComponent } from './catalog';
import { ConfidenceGauge } from '@/app/components/a2ui/catalog/ConfidenceGauge';
import { VoteSummary } from '@/app/components/a2ui/catalog/VoteSummary';
import { SignalCard } from '@/app/components/a2ui/catalog/SignalCard';
import { IndicatorChart } from '@/app/components/a2ui/catalog/IndicatorChart';
import { ActionBadge } from '@/app/components/a2ui/catalog/ActionBadge';
import { PriceDisplay } from '@/app/components/a2ui/catalog/PriceDisplay';
import { TextBlock } from '@/app/components/a2ui/catalog/TextBlock';
import { Row, Column } from '@/app/components/a2ui/catalog/Layout';
import { PositionCard } from '@/app/components/a2ui/catalog/PositionCard';
import { TradeButton } from '@/app/components/a2ui/catalog/TradeButton';
import { PerformanceTable } from '@/app/components/a2ui/catalog/PerformanceTable';
import { AlertCard } from '@/app/components/a2ui/catalog/AlertCard';
import { AllocationChart } from '@/app/components/a2ui/catalog/AllocationChart';
import { MetricCard } from '@/app/components/a2ui/catalog/MetricCard';
import { StatGroup } from '@/app/components/a2ui/catalog/StatGroup';
import { SparklineCard } from '@/app/components/a2ui/catalog/SparklineCard';
import { MiniBarChart } from '@/app/components/a2ui/catalog/MiniBarChart';
import { TimelineCard } from '@/app/components/a2ui/catalog/TimelineCard';
import { ProgressBar } from '@/app/components/a2ui/catalog/ProgressBar';

let registered = false;

export function registerA2UICatalog() {
  if (registered) return;
  registered = true;

  registerComponent('ConfidenceGauge', ConfidenceGauge as never);
  registerComponent('VoteSummary', VoteSummary as never);
  registerComponent('SignalCard', SignalCard as never);
  registerComponent('IndicatorChart', IndicatorChart as never);
  registerComponent('ActionBadge', ActionBadge as never);
  registerComponent('PriceDisplay', PriceDisplay as never);
  registerComponent('TextBlock', TextBlock as never);
  registerComponent('Row', Row as never);
  registerComponent('Column', Column as never);
  registerComponent('PositionCard', PositionCard as never);
  registerComponent('TradeButton', TradeButton as never);
  registerComponent('PerformanceTable', PerformanceTable as never);
  registerComponent('AlertCard', AlertCard as never);
  registerComponent('AllocationChart', AllocationChart as never);
  registerComponent('MetricCard', MetricCard as never);
  registerComponent('StatGroup', StatGroup as never);
  registerComponent('SparklineCard', SparklineCard as never);
  registerComponent('MiniBarChart', MiniBarChart as never);
  registerComponent('TimelineCard', TimelineCard as never);
  registerComponent('ProgressBar', ProgressBar as never);
}
