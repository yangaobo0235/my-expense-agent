package com.yangaobo.expense.backend.application.workflow;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;

class ExpenseWorkflowGraph {

    private final CompiledGraph<ExpenseWorkflowGraphState> compiledGraph;

    ExpenseWorkflowGraph(CompiledGraph<ExpenseWorkflowGraphState> compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    ExpenseWorkflowResult execute(Map<String, Object> initialState) {
        try {
            ExpenseWorkflowGraphState finalState =
                    compiledGraph
                            .invoke(
                                    GraphInput.args(initialState),
                                    RunnableConfig.builder()
                                            .threadId(
                                                    String.valueOf(
                                                            initialState.get(
                                                                    ExpenseWorkflowGraphState
                                                                            .RUN_ID)))
                                            .build())
                            .orElseThrow(() -> new IllegalStateException("经费合规审核图没有返回最终状态"));
            return finalState.workflowResult();
        } catch (RuntimeException exception) {
            throw unwrapGraphException(exception);
        }
    }

    private static RuntimeException unwrapGraphException(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null && shouldUnwrap(current)) {
            current = current.getCause();
        }
        return current instanceof RuntimeException runtimeException ? runtimeException : exception;
    }

    private static boolean shouldUnwrap(Throwable throwable) {
        return throwable instanceof CompletionException
                || throwable instanceof ExecutionException
                || "org.bsc.langgraph4j.GraphRunnerException"
                        .equals(throwable.getClass().getName());
    }
}
