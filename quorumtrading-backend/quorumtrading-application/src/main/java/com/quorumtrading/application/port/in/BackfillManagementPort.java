package com.quorumtrading.application.port.in;

import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.marketdata.BackfillJob;
import java.util.UUID;

public interface BackfillManagementPort {
    RequestBackfillResult requestBackfill(RequestBackfillCommand command);

    BackfillJob queryProgress(UUID jobId);
}
