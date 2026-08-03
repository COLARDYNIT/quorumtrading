/**
 * Strategy configuration schema types.
 * Used for client-side validation/linting of strategy YAML frontmatter.
 *
 * These types mirror the backend StrategySchemaResponse DTOs.
 * @see /api/v1/strategies/schema
 */

/**
 * Field definition with type, constraints, and defaults.
 */
export interface FieldDefinition {
  /** Field type: string, number, integer, boolean, array, object, map */
  type: 'string' | 'number' | 'integer' | 'boolean' | 'array' | 'object' | 'map';
  /** Default value if not specified */
  defaultValue?: unknown;
  /** Whether field is required */
  required: boolean;
  /** Human-readable description */
  description?: string;
  /** For number types: minimum allowed value */
  min?: number;
  /** For number types: maximum allowed value */
  max?: number;
  /** For enum-like strings: reference to enum definition */
  enumRef?: string;
  /** For arrays: item type definition */
  items?: FieldDefinition;
  /** For objects: nested field definitions */
  properties?: Record<string, FieldDefinition>;
}

/**
 * Single enum value with display name and description.
 */
export interface EnumValue {
  /** The actual value to use in YAML */
  value: string;
  /** Human-readable display name */
  displayName: string;
  /** Description of what this value does */
  description: string;
}

/**
 * Enum definition with all possible values.
 */
export interface EnumDefinition {
  /** Human-readable name for the enum */
  displayName: string;
  /** Description of what this enum controls */
  description: string;
  /** All possible enum values */
  values: EnumValue[];
}

/**
 * Conditional validation rule.
 * E.g., market_hours required when stock_tickers present.
 */
export interface ConditionalRule {
  /** Rule identifier for error messages */
  ruleId: string;
  /** Condition expression (e.g., 'stock_tickers.length > 0') */
  condition: string;
  /** Required field when condition is true */
  requiredField: string;
  /** Human-readable error message when validation fails */
  errorMessage: string;
}

/**
 * Complete strategy configuration schema.
 * Fetched from backend to drive client-side validation.
 */
export interface StrategySchemaResponse {
  /** Schema version for cache invalidation */
  schemaVersion: string;
  /** Field definitions for YAML frontmatter */
  fields: Record<string, FieldDefinition>;
  /** Enum definitions with values and descriptions */
  enums: Record<string, EnumDefinition>;
  /** Conditional validation rules */
  conditionalRules: ConditionalRule[];
}

/**
 * Lint error/warning produced by the validation engine.
 */
export interface LintDiagnostic {
  /** Severity: error, warning, or info */
  severity: 'error' | 'warning' | 'info';
  /** Error message */
  message: string;
  /** Line number (1-based) in the markdown */
  line: number;
  /** Column number (1-based) */
  column: number;
  /** End line (for range) */
  endLine?: number;
  /** End column (for range) */
  endColumn?: number;
  /** Error code for programmatic handling */
  code?: string;
  /** Related information or suggestions */
  suggestions?: string[];
}
