package com.quorumtrading.adapter.websocket.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillProgressMessageTest {

    @Test
    void completed_withRows_reportsRowsFetched() {
        BackfillProgressMessage message = BackfillProgressMessage.completed("job-1", 520);

        assertThat(message.getStatus()).isEqualTo("COMPLETED");
        assertThat(message.getTotalRecords()).isEqualTo(520);
        assertThat(message.getMessage()).isEqualTo("Backfill completed: 520 records fetched");
    }

    @Test
    void completed_withZeroRows_saysNoDataInsteadOfImplyingSuccess() {
        BackfillProgressMessage message = BackfillProgressMessage.completed("job-1", 0);

        assertThat(message.getTotalRecords()).isZero();
        assertThat(message.getMessage())
            .contains("0 records fetched")
            .contains("no data available");
        // Must not read like a successful fill.
        assertThat(message.getMessage()).doesNotContain("completed: 0 records\n");
    }

    @Test
    void completed_withNote_appendsContext() {
        BackfillProgressMessage message = BackfillProgressMessage.completed(
            "job-1", 0, "2 gap(s) had no data available from any provider for the requested range", null);

        assertThat(message.getMessage())
            .contains("0 records fetched")
            .contains("had no data available from any provider");
    }
}
