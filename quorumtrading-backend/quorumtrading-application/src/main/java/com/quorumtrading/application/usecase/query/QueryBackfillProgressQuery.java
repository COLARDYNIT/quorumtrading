package com.quorumtrading.application.usecase.query;

import java.util.UUID;

public record QueryBackfillProgressQuery(UUID jobId) {
    public QueryBackfillProgressQuery {
        if (jobId == null) throw new IllegalArgumentException("JobId cannot be null");
    }
}
