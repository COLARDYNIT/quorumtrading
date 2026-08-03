package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_parse_failures")
public class LlmParseFailureJpaEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "simulation_id")
    public UUID simulationId;

    @Column(name = "decision_timestamp")
    public Instant decisionTimestamp;

    @Column(length = 20)
    public String ticker;

    @Column(name = "agent_role", nullable = false, length = 30)
    public String agentRole;

    @Column(name = "requested_model")
    public String requestedModel;

    @Column(name = "actual_model")
    public String actualModel;

    public Double temperature;

    @Column(name = "execution_time_ms", nullable = false)
    public Integer executionTimeMs;

    @Column(columnDefinition = "TEXT")
    public String prompt;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    public String rawResponse;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "error_type")
    public String errorType;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
