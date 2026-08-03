package com.quorumtrading.model.strategy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parameters for strategy template customization.
 * LLM suggests parameter values based on market analysis.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 */
public class TemplateParameters {

    private final Map<String, Object> params;

    private TemplateParameters(Map<String, Object> params) {
        this.params = Map.copyOf(params);
    }

    public int getInt(String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        Object value = params.get(key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Object> params = new HashMap<>();

        public Builder put(String key, Object value) {
            Objects.requireNonNull(key, "Parameter key cannot be null");
            params.put(key, value);
            return this;
        }

        public Builder withInt(String key, int value) {
            Objects.requireNonNull(key, "Parameter key cannot be null");
            params.put(key, value);
            return this;
        }

        public Builder withDecimal(String key, BigDecimal value) {
            Objects.requireNonNull(key, "Parameter key cannot be null");
            Objects.requireNonNull(value, "Decimal value cannot be null");
            params.put(key, value);
            return this;
        }

        public Builder withString(String key, String value) {
            Objects.requireNonNull(key, "Parameter key cannot be null");
            params.put(key, value);
            return this;
        }

        public TemplateParameters build() {
            return new TemplateParameters(params);
        }
    }
}
