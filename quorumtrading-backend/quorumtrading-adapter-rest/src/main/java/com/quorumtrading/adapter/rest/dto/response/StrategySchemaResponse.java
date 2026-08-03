package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for strategy configuration schema.
 * Exposes validation rules so frontend can provide real-time linting.
 *
 * <p>This schema is the single source of truth for strategy YAML validation.
 * A Java test verifies this schema matches StrategyConfigParser behavior.</p>
 */
@Schema(description = "Strategy configuration schema for frontend validation")
public record StrategySchemaResponse(
    @Schema(description = "Schema version for cache invalidation")
    String schemaVersion,

    @Schema(description = "Field definitions for YAML frontmatter")
    Map<String, FieldDefinition> fields,

    @Schema(description = "Enum definitions with values and descriptions")
    Map<String, EnumDefinition> enums,

    @Schema(description = "Conditional validation rules")
    List<ConditionalRule> conditionalRules
) {

    /**
     * Definition of a single YAML field.
     */
    @Schema(description = "Field definition with type, constraints, and defaults")
    public record FieldDefinition(
        @Schema(description = "Field type: string, number, integer, boolean, array, object, map")
        String type,

        @Schema(description = "Default value if not specified")
        Object defaultValue,

        @Schema(description = "Whether field is required")
        boolean required,

        @Schema(description = "Human-readable description")
        String description,

        @Schema(description = "For number types: minimum allowed value")
        Double min,

        @Schema(description = "For number types: maximum allowed value")
        Double max,

        @Schema(description = "For enum-like strings: reference to enum definition")
        String enumRef,

        @Schema(description = "For arrays: item type definition")
        FieldDefinition items,

        @Schema(description = "For objects: nested field definitions")
        Map<String, FieldDefinition> properties
    ) {
        // Builder-style static factories for cleaner construction
        public static FieldDefinition string(String description, String defaultValue, boolean required) {
            return new FieldDefinition("string", defaultValue, required, description, null, null, null, null, null);
        }

        public static FieldDefinition number(String description, Double defaultValue, boolean required, Double min, Double max) {
            return new FieldDefinition("number", defaultValue, required, description, min, max, null, null, null);
        }

        public static FieldDefinition integer(String description, Integer defaultValue, boolean required, Integer min, Integer max) {
            Double minD = min != null ? min.doubleValue() : null;
            Double maxD = max != null ? max.doubleValue() : null;
            return new FieldDefinition("integer", defaultValue, required, description, minD, maxD, null, null, null);
        }

        public static FieldDefinition bool(String description, Boolean defaultValue, boolean required) {
            return new FieldDefinition("boolean", defaultValue, required, description, null, null, null, null, null);
        }

        public static FieldDefinition enumField(String description, String defaultValue, boolean required, String enumRef) {
            return new FieldDefinition("string", defaultValue, required, description, null, null, enumRef, null, null);
        }

        public static FieldDefinition array(String description, String itemType, boolean required) {
            return new FieldDefinition("array", null, required, description, null, null, null,
                new FieldDefinition(itemType, null, false, null, null, null, null, null, null), null);
        }

        public static FieldDefinition object(String description, boolean required, Map<String, FieldDefinition> properties) {
            return new FieldDefinition("object", null, required, description, null, null, null, null, properties);
        }

        public static FieldDefinition map(String description, String valueType, boolean required) {
            return new FieldDefinition("map", null, required, description, null, null, null,
                new FieldDefinition(valueType, null, false, null, null, null, null, null, null), null);
        }
    }

    /**
     * Definition of an enum type with all possible values.
     */
    @Schema(description = "Enum definition with values and descriptions")
    public record EnumDefinition(
        @Schema(description = "Human-readable name for the enum")
        String displayName,

        @Schema(description = "Description of what this enum controls")
        String description,

        @Schema(description = "All possible enum values")
        List<EnumValue> values
    ) {}

    /**
     * A single enum value with metadata.
     */
    @Schema(description = "Single enum value with display name and description")
    public record EnumValue(
        @Schema(description = "The actual value to use in YAML")
        String value,

        @Schema(description = "Human-readable display name")
        String displayName,

        @Schema(description = "Description of what this value does")
        String description
    ) {}

    /**
     * Conditional validation rule (e.g., live_schedule required when stock_tickers present).
     */
    @Schema(description = "Conditional validation rule")
    public record ConditionalRule(
        @Schema(description = "Rule identifier for error messages")
        String ruleId,

        @Schema(description = "Condition expression (e.g., 'stock_tickers.length > 0')")
        String condition,

        @Schema(description = "Required field when condition is true")
        String requiredField,

        @Schema(description = "Human-readable error message when validation fails")
        String errorMessage
    ) {}
}
