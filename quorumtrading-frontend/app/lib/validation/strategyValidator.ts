/**
 * Strategy YAML validation engine.
 * Uses the schema from backend to provide real-time linting.
 */

import type {
  StrategySchemaResponse,
  FieldDefinition,
  LintDiagnostic,
} from '../types/schema';
import * as yaml from 'yaml';

/**
 * Parse YAML frontmatter from markdown content.
 * Returns the parsed YAML object and line number mappings.
 */
interface FrontmatterParseResult {
  /** Parsed YAML as object, or null if parsing failed */
  data: Record<string, unknown> | null;
  /** Line number where frontmatter starts (1-based) */
  startLine: number;
  /** Line number where frontmatter ends (1-based) */
  endLine: number;
  /** Raw YAML string */
  yamlString: string;
  /** Parse error if any */
  parseError?: string;
}

/**
 * Extract and parse YAML frontmatter from markdown.
 */
function parseFrontmatter(markdown: string): FrontmatterParseResult {
  const lines = markdown.split('\n');

  // Check if markdown starts with ---
  if (!lines[0]?.trim().startsWith('---')) {
    return {
      data: null,
      startLine: 1,
      endLine: 1,
      yamlString: '',
      parseError: 'Strategy must start with YAML frontmatter (---)',
    };
  }

  // Find closing ---
  let endLineIndex = -1;
  for (let i = 1; i < lines.length; i++) {
    if (lines[i]?.trim() === '---') {
      endLineIndex = i;
      break;
    }
  }

  if (endLineIndex === -1) {
    return {
      data: null,
      startLine: 1,
      endLine: lines.length,
      yamlString: lines.slice(1).join('\n'),
      parseError: 'Missing closing --- for YAML frontmatter',
    };
  }

  const yamlString = lines.slice(1, endLineIndex).join('\n');

  try {
    const data = yaml.parse(yamlString) as Record<string, unknown>;
    return {
      data: data || {},
      startLine: 2, // Line after opening ---
      endLine: endLineIndex, // Line of closing ---
      yamlString,
    };
  } catch (e) {
    const error = e as Error;
    return {
      data: null,
      startLine: 2,
      endLine: endLineIndex,
      yamlString,
      parseError: `YAML parse error: ${error.message}`,
    };
  }
}

/**
 * Find the line number for a YAML key within the frontmatter.
 */
function findKeyLine(yamlString: string, key: string, startLine: number): number {
  const lines = yamlString.split('\n');
  const regex = new RegExp(`^\\s*${key}\\s*:`);

  for (let i = 0; i < lines.length; i++) {
    if (regex.test(lines[i])) {
      return startLine + i;
    }
  }

  return startLine; // Default to start if not found
}

/**
 * Validate a field value against its definition.
 */
function validateField(
  key: string,
  value: unknown,
  definition: FieldDefinition,
  schema: StrategySchemaResponse,
  yamlString: string,
  startLine: number
): LintDiagnostic[] {
  const diagnostics: LintDiagnostic[] = [];
  const line = findKeyLine(yamlString, key, startLine);

  // Type validation
  const actualType = getValueType(value);
  const expectedType = definition.type;

  if (value !== undefined && value !== null) {
    if (!isTypeCompatible(actualType, expectedType)) {
      diagnostics.push({
        severity: 'error',
        message: `'${key}' should be ${expectedType}, got ${actualType}`,
        line,
        column: 1,
        code: 'TYPE_MISMATCH',
      });
      return diagnostics; // Skip further validation if type is wrong
    }
  }

  // Required field validation
  if (definition.required && (value === undefined || value === null)) {
    diagnostics.push({
      severity: 'error',
      message: `'${key}' is required`,
      line: startLine,
      column: 1,
      code: 'REQUIRED_FIELD',
    });
    return diagnostics;
  }

  // Skip further validation if value is not set
  if (value === undefined || value === null) {
    return diagnostics;
  }

  // Enum validation
  if (definition.enumRef) {
    const enumDef = schema.enums[definition.enumRef];
    if (enumDef) {
      const validValues = enumDef.values.map(v => v.value);
      if (!validValues.includes(String(value))) {
        diagnostics.push({
          severity: 'error',
          message: `'${key}' must be one of: ${validValues.join(', ')}`,
          line,
          column: 1,
          code: 'INVALID_ENUM',
          suggestions: validValues,
        });
      }
    }
  }

  // Range validation for numbers
  if ((definition.type === 'number' || definition.type === 'integer') && typeof value === 'number') {
    if (definition.min !== undefined && definition.min !== null && value < definition.min) {
      diagnostics.push({
        severity: 'error',
        message: `'${key}' must be at least ${definition.min}`,
        line,
        column: 1,
        code: 'RANGE_ERROR',
      });
    }
    if (definition.max !== undefined && definition.max !== null && value > definition.max) {
      diagnostics.push({
        severity: 'error',
        message: `'${key}' must be at most ${definition.max}`,
        line,
        column: 1,
        code: 'RANGE_ERROR',
      });
    }
  }

  // Array validation
  if (definition.type === 'array' && Array.isArray(value)) {
    if (definition.items) {
      value.forEach((item, index) => {
        const itemDiagnostics = validateField(
          `${key}[${index}]`,
          item,
          definition.items!,
          schema,
          yamlString,
          startLine
        );
        diagnostics.push(...itemDiagnostics);
      });
    }
  }

  // Object/nested validation
  if (definition.type === 'object' && definition.properties && typeof value === 'object') {
    const objValue = value as Record<string, unknown>;
    for (const [propKey, propDef] of Object.entries(definition.properties)) {
      const propDiagnostics = validateField(
        `${key}.${propKey}`,
        objValue[propKey],
        propDef,
        schema,
        yamlString,
        startLine
      );
      diagnostics.push(...propDiagnostics);
    }
  }

  // Map validation (e.g., position_limits: {AAPL: 0.3, MSFT: 0.2})
  if (definition.type === 'map' && definition.items && typeof value === 'object' && !Array.isArray(value)) {
    const mapValue = value as Record<string, unknown>;
    const expectedValueType = definition.items.type;

    for (const [mapKey, mapVal] of Object.entries(mapValue)) {
      const mapValType = getValueType(mapVal);

      if (!isTypeCompatible(mapValType, expectedValueType)) {
        const mapKeyLine = findKeyLine(yamlString, mapKey, startLine);
        diagnostics.push({
          severity: 'error',
          message: `'${key}.${mapKey}' value should be ${expectedValueType}, got ${mapValType} (value: ${String(mapVal)})`,
          line: mapKeyLine,
          column: 1,
          code: 'MAP_VALUE_TYPE_MISMATCH',
        });
      }

      // Additional validation for number types
      if ((expectedValueType === 'number' || expectedValueType === 'integer') && typeof mapVal === 'number') {
        if (definition.items.min !== undefined && definition.items.min !== null && mapVal < definition.items.min) {
          const mapKeyLine = findKeyLine(yamlString, mapKey, startLine);
          diagnostics.push({
            severity: 'error',
            message: `'${key}.${mapKey}' must be at least ${definition.items.min}`,
            line: mapKeyLine,
            column: 1,
            code: 'RANGE_ERROR',
          });
        }
        if (definition.items.max !== undefined && definition.items.max !== null && mapVal > definition.items.max) {
          const mapKeyLine = findKeyLine(yamlString, mapKey, startLine);
          diagnostics.push({
            severity: 'error',
            message: `'${key}.${mapKey}' must be at most ${definition.items.max}`,
            line: mapKeyLine,
            column: 1,
            code: 'RANGE_ERROR',
          });
        }
      }
    }
  }

  return diagnostics;
}

/**
 * Get the JavaScript type of a value.
 */
function getValueType(value: unknown): string {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'array';
  if (typeof value === 'object') return 'object';
  return typeof value;
}

/**
 * Check if actual type is compatible with expected type.
 */
function isTypeCompatible(actual: string, expected: string): boolean {
  if (actual === expected) return true;

  // Integer can accept number
  if (expected === 'integer' && actual === 'number') return true;

  // Number can accept integer
  if (expected === 'number' && actual === 'number') return true;

  // Map is compatible with object
  if (expected === 'map' && actual === 'object') return true;

  return false;
}

/**
 * Evaluate a conditional rule.
 */
function evaluateCondition(
  condition: string,
  data: Record<string, unknown>
): boolean {
  // Simple condition evaluator for common patterns
  // stock_tickers.length > 0
  if (condition.includes('.length > 0')) {
    const key = condition.replace('.length > 0', '').trim();
    const value = data[key];
    return Array.isArray(value) && value.length > 0;
  }

  // market_hours != null
  if (condition.includes('!= null')) {
    const key = condition.replace('!= null', '').trim();
    return data[key] !== undefined && data[key] !== null;
  }

  // Fallback: assume false
  return false;
}

/**
 * Validate strategy markdown against the schema.
 * Main entry point for the validation engine.
 */
export function validateStrategy(
  markdown: string,
  schema: StrategySchemaResponse
): LintDiagnostic[] {
  const diagnostics: LintDiagnostic[] = [];

  // Parse frontmatter
  const parseResult = parseFrontmatter(markdown);

  if (parseResult.parseError) {
    diagnostics.push({
      severity: 'error',
      message: parseResult.parseError,
      line: parseResult.startLine,
      column: 1,
      code: 'PARSE_ERROR',
    });
    return diagnostics;
  }

  const data = parseResult.data || {};

  // Validate each field defined in schema
  for (const [key, definition] of Object.entries(schema.fields)) {
    const value = data[key];
    const fieldDiagnostics = validateField(
      key,
      value,
      definition,
      schema,
      parseResult.yamlString,
      parseResult.startLine
    );
    diagnostics.push(...fieldDiagnostics);
  }

  // Check for unknown fields (warnings, not errors)
  for (const key of Object.keys(data)) {
    if (!schema.fields[key]) {
      const line = findKeyLine(parseResult.yamlString, key, parseResult.startLine);
      diagnostics.push({
        severity: 'warning',
        message: `Unknown field '${key}' - will be ignored`,
        line,
        column: 1,
        code: 'UNKNOWN_FIELD',
      });
    }
  }

  // Evaluate conditional rules
  for (const rule of schema.conditionalRules) {
    if (evaluateCondition(rule.condition, data)) {
      // Condition is true, check if required field is present
      const requiredPath = rule.requiredField.split('.');
      let value: unknown = data;

      for (const part of requiredPath) {
        if (value && typeof value === 'object') {
          value = (value as Record<string, unknown>)[part];
        } else {
          value = undefined;
          break;
        }
      }

      if (value === undefined || value === null) {
        diagnostics.push({
          severity: 'error',
          message: rule.errorMessage,
          line: parseResult.startLine,
          column: 1,
          code: rule.ruleId.toUpperCase(),
        });
      }
    }
  }

  // Sort diagnostics by line number
  diagnostics.sort((a, b) => a.line - b.line);

  return diagnostics;
}

/**
 * Get autocomplete suggestions for a given position.
 */
export function getAutocompleteSuggestions(
  markdown: string,
  position: { line: number; column: number },
  schema: StrategySchemaResponse
): Array<{ label: string; description?: string; insertText: string }> {
  const suggestions: Array<{ label: string; description?: string; insertText: string }> = [];
  const lines = markdown.split('\n');
  const currentLine = lines[position.line - 1] || '';

  // Check if we're inside frontmatter
  let insideFrontmatter = false;
  let frontmatterEnd = 0;

  for (let i = 0; i < lines.length; i++) {
    if (i === 0 && lines[i].trim() === '---') {
      insideFrontmatter = true;
      continue;
    }
    if (insideFrontmatter && lines[i].trim() === '---') {
      frontmatterEnd = i;
      break;
    }
  }

  if (!insideFrontmatter || position.line > frontmatterEnd) {
    return suggestions;
  }

  // Check if we're starting a new key (line starts with optional whitespace)
  if (currentLine.trim() === '' || currentLine.match(/^\s*$/)) {
    // Suggest all top-level fields
    for (const [key, def] of Object.entries(schema.fields)) {
      suggestions.push({
        label: key,
        description: def.description,
        insertText: `${key}: ${getDefaultInsertText(def)}`,
      });
    }
  }

  // Check if we're typing a value for an enum field
  const keyMatch = currentLine.match(/^\s*(\w+):\s*$/);
  if (keyMatch) {
    const key = keyMatch[1];
    const def = schema.fields[key];
    if (def?.enumRef) {
      const enumDef = schema.enums[def.enumRef];
      if (enumDef) {
        for (const value of enumDef.values) {
          suggestions.push({
            label: value.value,
            description: value.description,
            insertText: value.value,
          });
        }
      }
    }
  }

  return suggestions;
}

/**
 * Get default insert text for a field definition.
 */
function getDefaultInsertText(def: FieldDefinition): string {
  if (def.defaultValue !== undefined && def.defaultValue !== null) {
    if (typeof def.defaultValue === 'string') {
      return def.defaultValue;
    }
    return String(def.defaultValue);
  }

  switch (def.type) {
    case 'string':
      return '""';
    case 'number':
    case 'integer':
      return '0';
    case 'boolean':
      return 'true';
    case 'array':
      return '\n  - ';
    case 'object':
      return '\n  ';
    default:
      return '';
  }
}
