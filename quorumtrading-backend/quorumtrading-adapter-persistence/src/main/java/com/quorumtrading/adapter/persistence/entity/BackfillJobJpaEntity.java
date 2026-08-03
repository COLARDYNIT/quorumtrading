package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for Backfill Job persistence.
 *
 * Maps to the backfill_jobs table created by Flyway migration V37.
 * This is SEPARATE from the REST DTO BackfillRequest to maintain hexagonal architecture boundaries.
 *
 * Uses standard JPA (not Panache) for cross-module compatibility.
 * JSONB columns store symbols and data types as JSON arrays for flexibility.
 */
@Entity
@Table(name = "backfill_jobs")
public class BackfillJobJpaEntity {

    /**
     * Primary key (UUID).
     */
    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    /**
     * Symbols to backfill (stored as JSONB array).
     * Example: ["BTC", "ETH", "AAPL"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "symbols", columnDefinition = "jsonb", nullable = false)
    private String symbols;

    /**
     * Data types to backfill (stored as JSONB array).
     * Example: ["PRICE", "NEWS", "SOCIAL"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_types", columnDefinition = "jsonb", nullable = false)
    private String dataTypes;

    /**
     * Start date of the backfill range.
     */
    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    /**
     * End date of the backfill range.
     */
    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    /**
     * Current status of the backfill job.
     * Enum values: PENDING, IN_PROGRESS, COMPLETED, FAILED
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Progress percentage (0-100).
     */
    @Column(name = "progress_percentage")
    private Integer progressPercentage;

    /**
     * Timestamp when the job was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Timestamp when processing started.
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Timestamp when processing completed (successfully or with failure).
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Error message if the job failed.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Total number of records expected to be processed.
     */
    @Column(name = "total_records_expected")
    private Integer totalRecordsExpected;

    /**
     * Number of records processed so far.
     */
    @Column(name = "records_processed")
    private Integer recordsProcessed;

    /**
     * Optional link to a simulation waiting for this backfill.
     * When backfill completes, fires SimulationDataReadyEvent.
     */
    @Column(name = "linked_simulation_id", columnDefinition = "UUID")
    private UUID linkedSimulationId;

    /**
     * Timestamp after which a RETRY_PENDING job should be retried.
     */
    @Column(name = "retry_after")
    private Instant retryAfter;

    /**
     * Bar granularity for the backfill (e.g., ONE_DAY, FIVE_MINUTES).
     * Defaults to ONE_DAY for backtests.
     */
    @Column(name = "granularity", length = 20)
    private String granularity;

    /**
     * JPA lifecycle callback to set default values before persistence.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (progressPercentage == null) {
            progressPercentage = 0;
        }
        if (recordsProcessed == null) {
            recordsProcessed = 0;
        }
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSymbols() {
        return symbols;
    }

    public void setSymbols(String symbols) {
        this.symbols = symbols;
    }

    public String getDataTypes() {
        return dataTypes;
    }

    public void setDataTypes(String dataTypes) {
        this.dataTypes = dataTypes;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getTotalRecordsExpected() {
        return totalRecordsExpected;
    }

    public void setTotalRecordsExpected(Integer totalRecordsExpected) {
        this.totalRecordsExpected = totalRecordsExpected;
    }

    public Integer getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(Integer recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }

    public UUID getLinkedSimulationId() {
        return linkedSimulationId;
    }

    public void setLinkedSimulationId(UUID linkedSimulationId) {
        this.linkedSimulationId = linkedSimulationId;
    }

    public Instant getRetryAfter() {
        return retryAfter;
    }

    public void setRetryAfter(Instant retryAfter) {
        this.retryAfter = retryAfter;
    }

    public String getGranularity() {
        return granularity;
    }

    public void setGranularity(String granularity) {
        this.granularity = granularity;
    }
}
