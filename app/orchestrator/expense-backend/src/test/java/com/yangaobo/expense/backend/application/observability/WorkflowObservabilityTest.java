package com.yangaobo.expense.backend.application.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkflowObservabilityTest {

    @Test
    void shouldExposeTraceIdAndCloseWorkflowSpan() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("expense.workflow")).thenReturn(span);
        when(span.tag(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(tracer.withSpan(span)).thenReturn(scope);
        AtomicReference<String> persisted = new AtomicReference<>();

        var result =
                new WorkflowObservability(tracer)
                        .workflow(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "request-1",
                                persisted::set,
                                () -> "done");

        assertThat(result.value()).isEqualTo("done");
        assertThat(result.traceId()).isEqualTo(persisted.get());
        verify(scope).close();
        verify(span).end();
    }
}
