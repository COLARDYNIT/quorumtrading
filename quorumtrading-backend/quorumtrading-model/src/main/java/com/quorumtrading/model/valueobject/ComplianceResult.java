package com.quorumtrading.model.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * Result of checking whether a strategy complies with a challenge's requirements.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * Immutable result of a domain operation. Contains structured violations
 * for UI display when a strategy does not meet challenge criteria.
 *
 * @param compliant true if the strategy meets all requirements
 * @param violations list of human-readable violation descriptions (empty if compliant)
 */
public record ComplianceResult(boolean compliant, List<String> violations) {

    public ComplianceResult {
        Objects.requireNonNull(violations, "violations list must not be null");
    }

    public static ComplianceResult pass() {
        return new ComplianceResult(true, List.of());
    }

    public static ComplianceResult fail(List<String> violations) {
        return new ComplianceResult(false, List.copyOf(violations));
    }

    public static ComplianceResult fail(String violation) {
        return new ComplianceResult(false, List.of(violation));
    }
}
