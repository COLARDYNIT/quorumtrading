package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BudgetStatusTest {

    @Test
    void shouldAllowProceedWhenAvailable() {
        assertThat(BudgetStatus.AVAILABLE.canProceed()).isTrue();
    }

    @Test
    void shouldAllowProceedWhenWarning() {
        assertThat(BudgetStatus.WARNING.canProceed()).isTrue();
    }

    @Test
    void shouldNotAllowProceedWhenDepleted() {
        assertThat(BudgetStatus.DEPLETED.canProceed()).isFalse();
    }
}
