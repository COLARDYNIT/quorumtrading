/**
 * Data Availability API types matching backend DTOs.
 * These types align with the backend's Data Availability feature.
 */

// ===== Enums =====

/**
 * Event types for market data.
 * Matches backend EventType enum.
 */
export enum EventType {
  PRICE = 'PRICE',
  NEWS = 'NEWS',
  SOCIAL = 'SOCIAL'
}

/**
 * Backfill job status.
 * Matches backend BackfillStatus enum.
 */
export enum BackfillStatus {
  PENDING = 'PENDING',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED'
}

/**
 * Backfill event types for WebSocket messages.
 */
export enum BackfillEventType {
  STARTED = 'STARTED',
  PROGRESS = 'PROGRESS',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED'
}

// ===== Request/Response Types =====

/**
 * Daily coverage metrics for a specific date.
 * Matches backend DailyCoverageDTO.
 */
export interface DailyCoverage {
  /** Date for this coverage metric (ISO date string) */
  date: string;
  /** Overall coverage percentage for this day */
  percentage: number;
  /** Per-type coverage for this day */
  perType: Record<string, number>;
  /** Per-symbol coverage for this day */
  perSymbol: Record<string, number>;
}

/**
 * Identified gap in market data.
 * Matches backend GapDTO.
 */
export interface DataGap {
  /** Symbol ticker */
  symbol: string;
  /** Data type with missing data */
  dataType: string;
  /** Gap start date (ISO date string) */
  from: string;
  /** Gap end date (ISO date string) */
  to: string;
  /**
   * Actionability classification:
   * ACTIONABLE | FUTURE | NO_SOURCE | BEFORE_HORIZON.
   * Optional — absent on older responses, in which case the UI falls back to a
   * local past-date heuristic.
   */
  classification?: string;
  /** Provider that would fill this gap, or null if no source supports it. */
  provider?: string | null;
  /** Estimated number of records a backfill would produce for this gap. */
  estimatedRecords?: number;
}

/**
 * Market data availability analysis response.
 * Matches backend DataAvailabilityResponse.
 * GET /api/v1/data-availability/coverage
 */
export interface DataAvailabilityResponse {
  /** Overall data coverage percentage across all symbols and types */
  overallCoverage: number;
  /** Coverage percentage per data type */
  perTypeCoverage: Record<string, number>;
  /** Coverage percentage per symbol */
  perSymbolCoverage: Record<string, number>;
  /** Daily coverage breakdown for each date in the range */
  dailyCoverage: DailyCoverage[];
  /** Identified gaps in data coverage */
  gaps: DataGap[];
  /** Whether data coverage meets the 80% sufficiency threshold */
  isSufficient: boolean;
}

/**
 * Request to backfill missing market data.
 * Matches backend BackfillRequest.
 * POST /api/v1/data-availability/backfill
 */
export interface BackfillRequest {
  /** Set of ticker symbols to backfill */
  symbols: string[];
  /** Set of data types to backfill */
  dataTypes: string[];
  /** Backfill start date (ISO date string) */
  from: string;
  /** Backfill end date (ISO date string) */
  to: string;
  /** Force backfill even if data is sufficient (skips coverage check) */
  forceBackfill?: boolean;
}

/**
 * Backfill job status and progress response.
 * Matches backend BackfillResponse.
 * POST /api/v1/data-availability/backfill (202 response)
 * GET /api/v1/data-availability/backfill/{jobId}
 */
export interface BackfillResponse {
  /** Unique backfill job identifier (UUID) */
  jobId: string | null;
  /** Current job status */
  status: string | null;
  /** Whether backfill was triggered or data was already sufficient */
  backfillTriggered: boolean;
  /** Human-readable status message */
  message: string;
  /** Progress percentage (0-100) */
  progressPercentage?: number;
  /** Timestamp when job was created (ISO-8601) */
  createdAt: string;
}

/**
 * Provider data-horizon entry for one (symbol, dataType).
 * Matches backend DataHorizonEntry.
 * GET /api/v1/data-availability/horizon
 */
export interface DataHorizonEntry {
  /** Symbol ticker */
  symbol: string;
  /** Data type (e.g. PRICE) */
  dataType: string;
  /** Earliest date the provider has data for (ISO date string), or null if unknown. */
  earliestAvailable: string | null;
  /** Whether the provider has any data for this (symbol, dataType). */
  hasData: boolean;
  /** When the horizon was probed (ISO-8601 instant), or null. */
  probedAt: string | null;
}

/**
 * Provider data-horizon response.
 * Matches backend DataHorizonResponse.
 * GET /api/v1/data-availability/horizon
 */
export interface DataHorizonResponse {
  /** One entry per (symbol, dataType) requested. */
  horizons: DataHorizonEntry[];
}

// ===== WebSocket Message Types =====

/**
 * Status of an individual gap during backfill.
 * Matches backend GapProgress.GapStatus enum.
 */
export enum GapStatus {
  PENDING = 'PENDING',
  IN_PROGRESS = 'IN_PROGRESS',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED',
  /** No data available for this range from any provider (backend now emits this). */
  NO_DATA = 'NO_DATA'
}

/**
 * Progress of an individual gap during backfill.
 * Matches backend GapProgressDTO.
 */
export interface GapProgressDTO {
  /** Symbol ticker (or "MARKET" for market-wide data) */
  symbol: string;
  /** Data type of this gap */
  dataType: string;
  /** Gap start date (ISO date string) */
  from: string;
  /** Gap end date (ISO date string) */
  to: string;
  /** Current status of this gap */
  status: GapStatus;
  /** Provider used to fill this gap (null if skipped/failed) */
  provider: string | null;
  /** Error message if failed/skipped */
  error: string | null;
  /** Number of events fetched (null if not processed) */
  eventsFetched: number | null;
}

/**
 * WebSocket message for backfill progress updates.
 * Matches backend BackfillProgressMessage.
 * Broadcasted via WebSocket during backfill operations.
 *
 * Note: This message does NOT extend BaseStreamMessage because backfill
 * operations are not tied to a specific simulation. They are global operations
 * that affect market data availability for all simulations.
 */
export interface BackfillProgressMessage {
  /** Message type (always 'BACKFILL_PROGRESS') */
  type: 'BACKFILL_PROGRESS';
  /** Timestamp of this progress update (ISO-8601) */
  timestamp: string;
  /** Unique backfill job identifier (UUID) */
  jobId: string;
  /** Current job status */
  status: BackfillStatus;
  /** Event type (STARTED, PROGRESS, COMPLETED, FAILED) */
  eventType: BackfillEventType;
  /** Progress percentage (0-100), null for FAILED */
  progressPercentage: number | null;
  /** Human-readable message */
  message: string;
  /** Total number of records to backfill */
  totalRecords: number | null;
  /** Number of records processed so far */
  processedRecords: number | null;
  /** The gap that was just processed (if available) */
  currentGap: GapProgressDTO | null;
  /** All gaps processed so far with status */
  gapDetails: GapProgressDTO[];
}
