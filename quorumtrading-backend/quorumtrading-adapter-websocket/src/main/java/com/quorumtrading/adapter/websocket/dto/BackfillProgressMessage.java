package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quorumtrading.model.marketdata.GapProgress;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * WebSocket message sent during backfill progress updates.
 * Represents real-time progress of market data backfill jobs.
 *
 * <p>Includes per-gap status for detailed frontend reporting:</p>
 * <ul>
 *   <li>currentGap: The gap that was just processed</li>
 *   <li>gapDetails: All gaps processed so far with status</li>
 * </ul>
 *
 * Note: This message does NOT implement BaseStreamMessage because backfill
 * operations are not tied to a specific simulation. They are global operations
 * that affect the market data availability for all simulations.
 */
@RegisterForReflection(methods = true, fields = true)
public class BackfillProgressMessage {

    @JsonProperty("type")
    private final String type;

    @JsonProperty("timestamp")
    private final String timestamp;

    @JsonProperty("jobId")
    private final String jobId;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("eventType")
    private final String eventType;

    @JsonProperty("progressPercentage")
    private final Integer progressPercentage;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("totalRecords")
    private final Integer totalRecords;

    @JsonProperty("processedRecords")
    private final Integer processedRecords;

    @JsonProperty("currentGap")
    private final GapProgressDTO currentGap;

    @JsonProperty("gapDetails")
    private final List<GapProgressDTO> gapDetails;

    @JsonProperty("linkedSimulationId")
    private final String linkedSimulationId;

    public BackfillProgressMessage(
            String type,
            String timestamp,
            String jobId,
            String status,
            String eventType,
            Integer progressPercentage,
            String message,
            Integer totalRecords,
            Integer processedRecords,
            GapProgressDTO currentGap,
            List<GapProgressDTO> gapDetails,
            String linkedSimulationId) {
        this.type = type;
        this.timestamp = timestamp;
        this.jobId = jobId;
        this.status = status;
        this.eventType = eventType;
        this.progressPercentage = progressPercentage;
        this.message = message;
        this.totalRecords = totalRecords;
        this.processedRecords = processedRecords;
        this.currentGap = currentGap;
        this.gapDetails = gapDetails;
        this.linkedSimulationId = linkedSimulationId;
    }

    /**
     * DTO for individual gap progress.
     */
    @RegisterForReflection(methods = true, fields = true)
    public static class GapProgressDTO {
        @JsonProperty("symbol")
        private final String symbol;

        @JsonProperty("dataType")
        private final String dataType;

        @JsonProperty("from")
        private final String from;

        @JsonProperty("to")
        private final String to;

        @JsonProperty("status")
        private final String status;

        @JsonProperty("provider")
        private final String provider;

        @JsonProperty("error")
        private final String error;

        @JsonProperty("eventsFetched")
        private final Integer eventsFetched;

        public GapProgressDTO(String symbol, String dataType, String from, String to,
                              String status, String provider, String error, Integer eventsFetched) {
            this.symbol = symbol;
            this.dataType = dataType;
            this.from = from;
            this.to = to;
            this.status = status;
            this.provider = provider;
            this.error = error;
            this.eventsFetched = eventsFetched;
        }

        public static GapProgressDTO from(GapProgress gap) {
            return new GapProgressDTO(
                gap.getSymbolDisplay(),
                gap.dataType().name(),
                gap.period().getStart().toString(),
                gap.period().getEnd().toString(),
                gap.status().name(),
                gap.providerName(),
                gap.errorMessage(),
                gap.eventsFetched()
            );
        }

        // Getters
        public String getSymbol() { return symbol; }
        public String getDataType() { return dataType; }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getStatus() { return status; }
        public String getProvider() { return provider; }
        public String getError() { return error; }
        public Integer getEventsFetched() { return eventsFetched; }
    }

    /**
     * Factory method for job created (queued) message.
     * Sent immediately when backfill is requested, before scheduler picks it up.
     */
    public static BackfillProgressMessage created(String jobId, int totalGaps) {
        return created(jobId, totalGaps, null);
    }

    /**
     * Factory method for job created (queued) message with linked simulation.
     */
    public static BackfillProgressMessage created(String jobId, int totalGaps, String linkedSimulationId) {
        return new BackfillProgressMessage(
            "BACKFILL_PROGRESS",
            java.time.Instant.now().toString(),
            jobId,
            "PENDING",
            "CREATED",
            0,
            String.format("Backfill queued: %d gaps to fill", totalGaps),
            totalGaps,
            0,
            null,
            List.of(),
            linkedSimulationId
        );
    }

    /**
     * Factory method for job started message.
     */
    public static BackfillProgressMessage started(String jobId) {
        return started(jobId, null);
    }

    /**
     * Factory method for job started message with linked simulation.
     */
    public static BackfillProgressMessage started(String jobId, String linkedSimulationId) {
        return new BackfillProgressMessage(
            "BACKFILL_PROGRESS",
            java.time.Instant.now().toString(),
            jobId,
            "IN_PROGRESS",
            "STARTED",
            0,
            "Backfill job started",
            null,
            null,
            null,
            List.of(),
            linkedSimulationId
        );
    }

    /**
     * Factory method for progress update message.
     */
    public static BackfillProgressMessage progress(String jobId, int percentage, int processed, int total) {
        return progress(jobId, percentage, processed, total, null);
    }

    /**
     * Factory method for progress update message with linked simulation.
     */
    public static BackfillProgressMessage progress(String jobId, int percentage, int processed, int total, String linkedSimulationId) {
        return new BackfillProgressMessage(
            "BACKFILL_PROGRESS",
            java.time.Instant.now().toString(),
            jobId,
            "IN_PROGRESS",
            "PROGRESS",
            percentage,
            String.format("Processing: %d/%d records", processed, total),
            total,
            processed,
            null,
            List.of(),
            linkedSimulationId
        );
    }

    /**
     * Factory method for progress update message with per-gap details.
     */
    public static BackfillProgressMessage progressWithGaps(
            String jobId, int percentage, int processed, int total,
            GapProgressDTO currentGap, List<GapProgressDTO> gapDetails) {
        return progressWithGaps(jobId, percentage, processed, total, currentGap, gapDetails, null);
    }

    /**
     * Factory method for progress update message with per-gap details and linked simulation.
     */
    public static BackfillProgressMessage progressWithGaps(
            String jobId, int percentage, int processed, int total,
            GapProgressDTO currentGap, List<GapProgressDTO> gapDetails, String linkedSimulationId) {
        return new BackfillProgressMessage(
            "BACKFILL_PROGRESS",
            java.time.Instant.now().toString(),
            jobId,
            "IN_PROGRESS",
            "PROGRESS",
            percentage,
            String.format("Processing: %d/%d gaps", processed, total),
            total,
            processed,
            currentGap,
            gapDetails,
            linkedSimulationId
        );
    }

    /**
     * Factory method for job completed message.
     */
    public static BackfillProgressMessage completed(String jobId, int recordsFetched) {
        return completed(jobId, recordsFetched, null, null);
    }

    /**
     * Factory method for job completed message with linked simulation.
     */
    public static BackfillProgressMessage completed(String jobId, int recordsFetched, String linkedSimulationId) {
        return completed(jobId, recordsFetched, null, linkedSimulationId);
    }

    /**
     * Factory method for job completed message with an honest result note.
     *
     * <p>{@code recordsFetched} is the number of rows actually fetched and stored — NOT
     * the number of gaps processed. A job can complete having stored zero rows when no
     * provider has data for the requested range; the message says so explicitly instead
     * of implying data was filled.</p>
     *
     * @param recordsFetched rows fetched and stored (0 if nothing was available)
     * @param note optional context (e.g. gaps with no data / no provider), may be null
     */
    public static BackfillProgressMessage completed(String jobId, int recordsFetched, String note, String linkedSimulationId) {
        String message = recordsFetched > 0
            ? String.format("Backfill completed: %d records fetched", recordsFetched)
            : "Backfill completed: 0 records fetched — no data available from providers for this range";
        if (note != null && !note.isBlank()) {
            message = message + " (" + note + ")";
        }
        return new BackfillProgressMessage(
            "BACKFILL_PROGRESS",
            java.time.Instant.now().toString(),
            jobId,
            "COMPLETED",
            "COMPLETED",
            100,
            message,
            recordsFetched,
            recordsFetched,
            null,
            List.of(),
            linkedSimulationId
        );
    }

    /**
     * Factory method for job failed message.
     */
    public static BackfillProgressMessage failed(String jobId, String error) {
        return failed(jobId, error, null);
    }

    /**
     * Factory method for job failed message with linked simulation.
     */
    public static BackfillProgressMessage failed(String jobId, String error, String linkedSimulationId) {
        return new BackfillProgressMessage(
            "BACKFILL_PROGRESS",
            java.time.Instant.now().toString(),
            jobId,
            "FAILED",
            "FAILED",
            null,
            "Backfill failed: " + error,
            null,
            null,
            null,
            List.of(),
            linkedSimulationId
        );
    }

    // Getters
    public String getType() {
        return type;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getJobId() {
        return jobId;
    }

    public String getStatus() {
        return status;
    }

    public String getEventType() {
        return eventType;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public String getMessage() {
        return message;
    }

    public Integer getTotalRecords() {
        return totalRecords;
    }

    public Integer getProcessedRecords() {
        return processedRecords;
    }

    public GapProgressDTO getCurrentGap() {
        return currentGap;
    }

    public List<GapProgressDTO> getGapDetails() {
        return gapDetails;
    }

    public String getLinkedSimulationId() {
        return linkedSimulationId;
    }
}
