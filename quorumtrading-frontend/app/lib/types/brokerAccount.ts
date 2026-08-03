/**
 * Broker Account Types
 *
 * TypeScript types for the broker account management API.
 * Matches backend DTOs in quorumtrading-adapter-rest.
 */

/**
 * Type of broker connection
 */
export type BrokerType = 'MOCK' | 'ALPACA_PAPER' | 'ALPACA_LIVE' | 'SAXO_SIM' | 'SAXO_LIVE';

/**
 * Account status indicating connection health
 */
export type AccountStatus = 'ACTIVE' | 'INVALID_CREDENTIALS' | 'DISABLED';

/**
 * Cost simulation preset for mock broker accounts
 */
export type CostPreset = 'ZERO_FEES' | 'TYPICAL_RETAIL' | 'HIGH_FRICTION' | 'CUSTOM';

/**
 * Money value with currency
 */
export interface Money {
  amount: number;
  currency: string;
}

/**
 * Configuration for mock broker cost simulation
 */
export interface MockBrokerConfig {
  /** Starting capital amount in USD */
  initialCapital: number;
  /** Cost simulation preset (determines commission/slippage) */
  preset: CostPreset;
  /** Stock commission rate per trade (0-1) */
  commissionRate?: number;
  /** Crypto commission rate per trade (0-1) */
  cryptoCommissionRate?: number;
  /** Base slippage per trade (0-1) */
  slippageBase?: number;
  /** Slippage increase per unit traded */
  slippageImpactPerUnit?: number;
  /** Slippage multiplier based on volatility */
  slippageVolatilityMultiplier?: number;
}

/**
 * Broker account response from API
 */
export interface BrokerAccount {
  id: string;
  userId: string;
  name: string;
  brokerType: BrokerType;
  totalBalance: Money;
  /** Total equity (cash + positions market value). Null for MOCK accounts. */
  equity?: Money;
  reservedBalance: Money;
  availableBalance: Money;
  status: AccountStatus;
  /** Configuration for MOCK broker type */
  mockConfig?: MockBrokerConfig;
  createdAt: string;
  updatedAt: string;
}

/**
 * Credentials for connecting to a real broker
 */
export interface BrokerCredentials {
  apiKey: string;
  apiSecret: string;
}

/**
 * Request to create a new broker account
 */
export interface CreateBrokerAccountRequest {
  /** Display name for the account */
  name: string;
  /** Type of broker connection */
  brokerType: BrokerType;
  /** Credentials for ALPACA_PAPER or ALPACA_LIVE */
  credentials?: BrokerCredentials;
  /** Configuration for MOCK broker type */
  mockConfig?: MockBrokerConfig;
}

/**
 * Request to update broker account credentials (and optionally name)
 */
export interface UpdateBrokerAccountCredentialsRequest {
  /** New display name (null/undefined keeps current) */
  name?: string;
  /** New API credentials */
  credentials: BrokerCredentials;
}

/**
 * @deprecated The validate endpoint returns a full BrokerAccount response.
 * Use BrokerAccount type instead. Kept for backward compatibility.
 */
export type ValidateBrokerAccountResponse = BrokerAccount;

/**
 * Helper to get human-readable broker type label
 */
export function getBrokerTypeLabel(type: BrokerType): string {
  switch (type) {
    case 'MOCK':
      return 'Mock (Simulated)';
    case 'ALPACA_PAPER':
      return 'Alpaca Paper Trading';
    case 'ALPACA_LIVE':
      return 'Alpaca Live Trading';
    case 'SAXO_SIM':
      return 'Saxo (Sim)';
    case 'SAXO_LIVE':
      return 'Saxo (Live)';
    default:
      return type;
  }
}

/**
 * Helper to get human-readable status label
 */
export function getStatusLabel(status: AccountStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'Active';
    case 'INVALID_CREDENTIALS':
      return 'Invalid Credentials';
    case 'DISABLED':
      return 'Disabled';
    default:
      return status;
  }
}

/**
 * Helper to get status color for UI display
 */
export function getStatusColor(status: AccountStatus): 'success' | 'error' | 'warning' {
  switch (status) {
    case 'ACTIVE':
      return 'success';
    case 'INVALID_CREDENTIALS':
      return 'error';
    case 'DISABLED':
      return 'warning';
    default:
      return 'warning';
  }
}

/**
 * Helper to get cost preset description
 */
export function getCostPresetDescription(preset: CostPreset): string {
  switch (preset) {
    case 'ZERO_FEES':
      return 'No fees or slippage - pure strategy performance testing';
    case 'TYPICAL_RETAIL':
      return 'Alpaca-aligned costs (0% stocks, 0.25% crypto, moderate slippage)';
    case 'HIGH_FRICTION':
      return 'High costs for stress testing (~0.5% commission, high slippage)';
    case 'CUSTOM':
      return 'Custom fee and slippage configuration';
    default:
      return preset;
  }
}

/**
 * Cost values for each preset (matches backend CostPreset.java)
 */
export interface CostPresetValues {
  commissionRate: number;       // Stock commission as decimal (0.0 = 0%)
  cryptoCommissionRate: number; // Crypto commission as decimal (0.0025 = 0.25%)
  slippageBase: number;         // As decimal (0.0005 = 0.05%)
  label: string;
}

/**
 * Get the actual cost values for a preset
 */
export function getCostPresetValues(preset: CostPreset): CostPresetValues {
  switch (preset) {
    case 'ZERO_FEES':
      return { commissionRate: 0, cryptoCommissionRate: 0, slippageBase: 0, label: 'Zero Fees' };
    case 'TYPICAL_RETAIL':
      return { commissionRate: 0, cryptoCommissionRate: 0.0025, slippageBase: 0.0005, label: 'Retail' };
    case 'HIGH_FRICTION':
      return { commissionRate: 0.005, cryptoCommissionRate: 0.005, slippageBase: 0.002, label: 'High Friction' };
    case 'CUSTOM':
      return { commissionRate: 0, cryptoCommissionRate: 0, slippageBase: 0, label: 'Custom' }; // Values come from config
    default:
      return { commissionRate: 0, cryptoCommissionRate: 0, slippageBase: 0, label: preset };
  }
}

/**
 * Format money value for display
 */
export function formatMoney(money: Money): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: money.currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(money.amount);
}
