package com.yangaobo.expense.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExpenseBackendApplicationTest {

    @Test
    void applicationEntryPointRemainsAvailable() {
        assertThat(ExpenseBackendApplication.class).isNotNull();
    }
}
