package com.yangaobo.expense.backend.application.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class WorkflowObservability {

    private final Tracer tracer;

    public WorkflowObservability(Tracer tracer) {
        this.tracer = tracer;
    }

    public <T> TraceResult<T> workflow(
            UUID caseId,
            UUID runId,
            String requestId,
            Consumer<String> traceStarted,
            Supplier<T> operation) {
        Span span =
                tracer.nextSpan()
                        .name("expense.workflow")
                        .tag("expense.case.id", caseId.toString())
                        .tag("expense.run.id", runId.toString())
                        .tag("expense.request.id", requestId)
                        .start();
        String traceId = span.context().traceId();
        traceStarted.accept(traceId);
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return new TraceResult<>(traceId, operation.get());
        } catch (RuntimeException exception) {
            span.error(exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    public <T> T step(
            String stepName, UUID caseId, UUID runId, Supplier<T> operation) {
        Span span =
                tracer.nextSpan()
                        .name("expense.workflow." + stepName.toLowerCase())
                        .tag("expense.workflow.step", stepName)
                        .tag("expense.case.id", caseId.toString())
                        .tag("expense.run.id", runId.toString())
                        .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            T result = operation.get();
            span.tag("expense.step.status", "SUCCEEDED");
            return result;
        } catch (RuntimeException exception) {
            span.tag("expense.step.status", "FAILED");
            span.error(exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    public record TraceResult<T>(String traceId, T value) {}
}
