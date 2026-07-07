package com.yangaobo.expense.backend.infrastructure.mcp;

import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import com.yangaobo.expense.backend.application.governance.DependencyCircuitBreaker;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class McpRetryExecutor {

    private final int maxAttempts;
    private final Duration retryDelay;
    private final DependencyCircuitBreaker circuitBreaker;

    McpRetryExecutor(int maxAttempts, Duration retryDelay) {
        this(
                maxAttempts,
                retryDelay,
                new DependencyCircuitBreaker(
                        java.time.Clock.systemUTC()));
    }

    McpRetryExecutor(
            int maxAttempts,
            Duration retryDelay,
            DependencyCircuitBreaker circuitBreaker) {
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException(
                    "MCP maxAttempts 必须处于 1 到 5");
        }
        this.maxAttempts = maxAttempts;
        this.retryDelay =
                retryDelay == null ? Duration.ZERO : retryDelay;
        this.circuitBreaker = circuitBreaker;
    }

    <T> T execute(String operation, Supplier<T> supplier) {
        return circuitBreaker.execute("mcp:" + operation, () -> executeWithRetry(operation, supplier));
    }

    private <T> T executeWithRetry(String operation, Supplier<T> supplier) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (!retryable(exception) || attempt == maxAttempts) {
                    break;
                }
                pause();
            }
        }
        ExpenseFlowException failure =
                new ExpenseFlowException(
                        ExpenseFlowErrorCode.DEPENDENCY_UNAVAILABLE,
                        "MCP 调用失败："
                                + operation
                                + "，"
                                + classification(lastFailure));
        failure.initCause(lastFailure);
        throw failure;
    }

    private void pause() {
        if (retryDelay.isZero() || retryDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 重试等待被中断", exception);
        }
    }

    static boolean retryable(Throwable failure) {
        for (Throwable current = failure;
                current != null;
                current = current.getCause()) {
            if (current instanceof HttpTimeoutException
                    || current instanceof HttpConnectTimeoutException
                    || current instanceof ConnectException
                    || current instanceof TimeoutException) {
                return true;
            }
        }
        String message =
                String.valueOf(failure.getMessage())
                        .toLowerCase(Locale.ROOT);
        return message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection refused")
                || message.contains("429")
                || message.matches(".*\\b5\\d\\d\\b.*");
    }

    private static String classification(Throwable failure) {
        return retryable(failure)
                ? "RETRYABLE_DEPENDENCY_FAILURE"
                : "NON_RETRYABLE_DEPENDENCY_FAILURE";
    }
}
