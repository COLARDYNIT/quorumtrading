#!/usr/bin/env ts-node
/**
 * OpenAPI Contract Validator
 *
 * Validates that frontend TypeScript types match the backend OpenAPI specification.
 * This prevents API contract drift and catches mismatches during development.
 *
 * Usage:
 *   npm run validate:api          # Validate against running backend
 *   npm run validate:api:file     # Validate against committed spec file
 */

import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';
import * as https from 'https';
import * as http from 'http';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

interface OpenAPISchema {
  type?: string;
  format?: string;
  enum?: string[];
  properties?: Record<string, OpenAPISchema>;
  required?: string[];
  items?: OpenAPISchema;
  $ref?: string;
  description?: string;
  examples?: any[];
}

interface OpenAPISpec {
  components?: {
    schemas?: Record<string, OpenAPISchema>;
  };
}

interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

// TypeScript type definitions (simplified representation)
const frontendTypes = {
  StrategyResponse: {
    id: 'string',
    name: 'string',
    version: 'number',
    markdownContent: 'string',
    createdAt: 'string',
  },
  CreateStrategyRequest: {
    name: 'string',
    markdownContent: 'string',
  },
  SimulationResponse: {
    id: 'string',
    strategyId: 'string',
    startDate: 'string',
    endDate: 'string',
    status: "'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'",
    name: 'string',
    progress: 'number',
    createdAt: 'string',
  },
  CreateSimulationRequest: {
    strategyId: 'string',
    startDate: 'string',
    endDate: 'string',
    name: 'string',
  },
  MarketEvent: {
    id: 'string',
    timestamp: 'string',
    eventType: 'EventType',
    source: 'string',
    symbol: 'string',
    rawData: 'Record<string, unknown>',
    ingestedAt: 'string',
  },
  MarketDataResponse: {
    events: 'MarketEvent[]',
    total: 'number',
    from: 'string',
    to: 'string',
    limit: 'number?',
  },
  LLMDecision: {
    id: 'string',
    simulationId: 'string',
    agentType: 'string',
    decisionTimestamp: 'string',
    reasoning: 'string',
    suggestedAction: 'string',
    confidence: 'number',
  },
  LLMDecisionsResponse: {
    decisions: 'LLMDecision[]',
    total: 'number',
    simulationId: 'string',
    limit: 'number',
  },
  ConsensusDecision: {
    id: 'string',
    simulationId: 'string',
    decisionTimestamp: 'string',
    consensusMethod: 'ConsensusMethod',
    finalAction: 'string',
    executed: 'boolean',
    notes: 'string?',
  },
  ConsensusDecisionsResponse: {
    decisions: 'ConsensusDecision[]',
    total: 'number',
    simulationId: 'string',
    limit: 'number',
  },
  PortfolioSnapshot: {
    id: 'string',
    simulationId: 'string',
    timestamp: 'string',
    cashBalance: 'number',
    positions: 'Record<string, unknown>',
    totalValue: 'number',
  },
  PortfolioSnapshotsResponse: {
    snapshots: 'PortfolioSnapshot[]',
    total: 'number',
    simulationId: 'string',
    limit: 'number',
    from: 'string',
    to: 'string',
  },
};

async function fetchOpenAPISpec(url: string): Promise<OpenAPISpec> {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https') ? https : http;
    client.get(url, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try {
          const spec = yaml.load(data) as OpenAPISpec;
          resolve(spec);
        } catch (e) {
          reject(new Error(`Failed to parse OpenAPI spec: ${e}`));
        }
      });
    }).on('error', reject);
  });
}

function loadOpenAPISpecFromFile(filePath: string): OpenAPISpec {
  const content = fs.readFileSync(filePath, 'utf8');
  return yaml.load(content) as OpenAPISpec;
}

function mapOpenAPITypeToTS(schema: OpenAPISchema, spec: OpenAPISpec): string {
  // Resolve $ref
  if (schema.$ref) {
    const refPath = schema.$ref.split('/');
    const schemaName = refPath[refPath.length - 1];

    // Special handling for common Java types
    if (schemaName === 'Instant' || schemaName === 'UUID') {
      return 'string';
    }

    // For other refs, try to resolve the actual schema
    if (spec.components?.schemas?.[schemaName]) {
      const referencedSchema = spec.components.schemas[schemaName];
      // Avoid infinite recursion - if referenced schema has a $ref, just return the name
      if (referencedSchema.$ref) {
        return schemaName;
      }
      return mapOpenAPITypeToTS(referencedSchema, spec);
    }

    return schemaName;
  }

  if (schema.enum) {
    return schema.enum.map(v => `'${v}'`).join(' | ');
  }

  switch (schema.type) {
    case 'string':
      return 'string';
    case 'integer':
    case 'number':
      return 'number';
    case 'boolean':
      return 'boolean';
    case 'array':
      if (schema.items) {
        const itemType = mapOpenAPITypeToTS(schema.items, spec);
        return `${itemType}[]`;
      }
      return 'any[]';
    case 'object':
      if (schema.properties) {
        return 'Record<string, unknown>';
      }
      return 'object';
    default:
      return 'unknown';
  }
}

function validateType(
  typeName: string,
  frontendType: Record<string, string>,
  backendSchema: OpenAPISchema,
  spec: OpenAPISpec
): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  if (!backendSchema.properties) {
    errors.push(`Backend schema for ${typeName} has no properties`);
    return { valid: false, errors, warnings };
  }

  const backendFields = new Set(Object.keys(backendSchema.properties));
  const frontendFields = new Set(Object.keys(frontendType));
  const requiredFields = new Set(backendSchema.required || []);

  // Check for missing required fields in frontend
  for (const field of requiredFields) {
    if (!frontendFields.has(field)) {
      errors.push(`${typeName}: Missing required field '${field}' in frontend type`);
    }
  }

  // Check for extra fields in frontend
  for (const field of frontendFields) {
    if (!backendFields.has(field)) {
      warnings.push(`${typeName}: Frontend has extra field '${field}' not in backend schema`);
    }
  }

  // Check field types
  for (const field of frontendFields) {
    if (backendFields.has(field)) {
      const frontendFieldType = frontendType[field];
      const backendFieldSchema = backendSchema.properties[field];
      const backendFieldType = mapOpenAPITypeToTS(backendFieldSchema, spec);

      const isOptional = frontendFieldType.endsWith('?');
      const frontendBaseType = frontendFieldType.replace('?', '');
      const isRequired = requiredFields.has(field);

      // Check optional/required mismatch
      if (isRequired && isOptional) {
        errors.push(`${typeName}.${field}: Marked as optional in frontend but required in backend`);
      }
      if (!isRequired && !isOptional && !frontendFieldType.includes('|')) {
        warnings.push(`${typeName}.${field}: Not marked as optional in frontend but not required in backend`);
      }

      // Check type compatibility (simplified)
      if (!frontendBaseType.includes(backendFieldType) && !backendFieldType.includes(frontendBaseType)) {
        // Allow some flexibility for complex types
        if (!(frontendBaseType.includes('Record') && backendFieldType === 'object') &&
            !(frontendBaseType.includes('[]') && backendFieldType.includes('[]'))) {
          errors.push(`${typeName}.${field}: Type mismatch - Frontend: ${frontendFieldType}, Backend: ${backendFieldType}`);
        }
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

async function main() {
  const args = process.argv.slice(2);
  const useFile = args.includes('--file');

  console.log('🔍 OpenAPI Contract Validator\n');

  let spec: OpenAPISpec;

  try {
    if (useFile) {
      const specPath = path.join(__dirname, '../../quorumtrading-backend/openapi-spec/openapi.yaml');
      console.log(`📄 Loading OpenAPI spec from file: ${specPath}\n`);

      if (!fs.existsSync(specPath)) {
        console.error('❌ OpenAPI spec file not found. Please ensure backend has generated it.');
        console.error('   Run: cd quorumtrading-backend && ./mvnw compile\n');
        process.exit(1);
      }

      spec = loadOpenAPISpecFromFile(specPath);
    } else {
      const backendUrl = process.env.BACKEND_URL || 'http://localhost:8085';
      const openApiUrl = `${backendUrl}/q/openapi`;

      console.log(`📡 Fetching OpenAPI spec from: ${openApiUrl}\n`);
      spec = await fetchOpenAPISpec(openApiUrl);
    }

    if (!spec.components?.schemas) {
      console.error('❌ OpenAPI spec has no component schemas\n');
      process.exit(1);
    }

    console.log('✅ OpenAPI spec loaded successfully\n');
    console.log('📋 Validating frontend types against backend schema...\n');

    let totalErrors = 0;
    let totalWarnings = 0;

    // Validate each frontend type
    for (const [typeName, frontendType] of Object.entries(frontendTypes)) {
      const backendSchema = spec.components.schemas[typeName];

      if (!backendSchema) {
        console.log(`⚠️  ${typeName}: Not found in backend schema (frontend-only type?)`);
        totalWarnings++;
        continue;
      }

      const result = validateType(typeName, frontendType, backendSchema, spec);

      if (result.valid && result.warnings.length === 0) {
        console.log(`✅ ${typeName}: Valid`);
      } else {
        if (result.errors.length > 0) {
          console.log(`❌ ${typeName}: INVALID`);
          result.errors.forEach(err => console.log(`   - ${err}`));
          totalErrors += result.errors.length;
        }
        if (result.warnings.length > 0) {
          console.log(`⚠️  ${typeName}: Warnings`);
          result.warnings.forEach(warn => console.log(`   - ${warn}`));
          totalWarnings += result.warnings.length;
        }
      }
    }

    console.log('\n' + '='.repeat(70));
    console.log(`\n📊 Validation Summary:`);
    console.log(`   Types checked: ${Object.keys(frontendTypes).length}`);
    console.log(`   Errors: ${totalErrors}`);
    console.log(`   Warnings: ${totalWarnings}\n`);

    if (totalErrors > 0) {
      console.error('❌ Validation FAILED - API contract mismatch detected\n');
      process.exit(1);
    } else if (totalWarnings > 0) {
      console.log('⚠️  Validation passed with warnings\n');
      process.exit(0);
    } else {
      console.log('✅ All types match backend OpenAPI specification\n');
      process.exit(0);
    }

  } catch (error) {
    console.error(`❌ Error: ${error instanceof Error ? error.message : error}\n`);
    process.exit(1);
  }
}

// Run if executed directly
main();
