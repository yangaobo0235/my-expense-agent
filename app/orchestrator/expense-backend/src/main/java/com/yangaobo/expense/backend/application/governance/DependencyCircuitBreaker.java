package com.yangaobo.expense.backend.application.governance;

import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class DependencyCircuitBreaker {

    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final int failureThreshold = 3;
    private final Duration openDuration = Duration.ofSeconds(30);

    public DependencyCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    public <T> T execute(String dependency, Supplier<T> supplier) {
        State state = states.computeIfAbsent(dependency, ignored -> new State());
        Instant now = clock.instant();
        synchronized (state) {
            if (state.openedAt != null && now.isBefore(state.openedAt.plus(openDuration))) {
                throw new MyExpenseAgentException(
                        MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE,
                        "依赖熔断中：" + dependency);
            }
            if (state.openedAt != null) {
                state.openedAt = null;
                state.failures = 0;
            }
        }
        try {
            T result = supplier.get();
            synchronized (state) {
                state.failures = 0;
                state.openedAt = null;
            }
            return result;
        } catch (RuntimeException exception) {
            synchronized (state) {
                state.failures++;
                if (state.failures >= failureThreshold) {
                    state.openedAt = clock.instant();
                }
            }
            throw exception;
        }
    }

    private static final class State {
        private int failures;
        private Instant openedAt;
    }
}
