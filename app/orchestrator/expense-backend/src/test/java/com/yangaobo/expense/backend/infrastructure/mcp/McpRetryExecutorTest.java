package com.yangaobo.expense.backend.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class McpRetryExecutorTest {

    @Test
    void shouldRetryTransientConnectionFailure() {
        AtomicInteger attempts = new AtomicInteger();
        McpRetryExecutor executor =
                new McpRetryExecutor(2, Duration.ZERO);

        String result =
                executor.execute(
                        "get_employee_profile",
                        () -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException(
                                        new ConnectException(
                                                "connection refused"));
                            }
                            return "ok";
                        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void shouldNotRetryNonTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        McpRetryExecutor executor =
                new McpRetryExecutor(3, Duration.ZERO);

        assertThatThrownBy(
                        () ->
                                executor.execute(
                                        "get_employee_profile",
                                        () -> {
                                            attempts.incrementAndGet();
                                            throw new IllegalArgumentException(
                                                    "invalid arguments");
                                        }))
                .isInstanceOfSatisfying(
                        ExpenseFlowException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ExpenseFlowErrorCode
                                                        .DEPENDENCY_UNAVAILABLE));
        assertThat(attempts).hasValue(1);
    }
}
