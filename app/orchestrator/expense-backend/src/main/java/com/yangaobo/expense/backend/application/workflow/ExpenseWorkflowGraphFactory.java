package com.yangaobo.expense.backend.application.workflow;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.Map;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

@Component
class ExpenseWorkflowGraphFactory {

    private static final String RESTORE_ROUTE = "RESTORE";
    private static final String EXECUTE_ROUTE = "EXECUTE";

    private final ExpenseWorkflowGraph graph;

    ExpenseWorkflowGraphFactory(ExpenseWorkflowSteps steps) {
        this.graph = build(steps);
    }

    ExpenseWorkflowGraph graph() {
        return graph;
    }

    private static ExpenseWorkflowGraph build(ExpenseWorkflowSteps steps) {
        try {
            StateGraph<ExpenseWorkflowGraphState> stateGraph =
                    new StateGraph<>(ExpenseWorkflowGraphState::new);
            stateGraph
                    .addNode(
                            ExpenseWorkflowNodeNames.RESTORE_WORKFLOW,
                            node_async(steps::restoreResultNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.LOAD_EXTRACTED,
                            node_async(steps::loadExtractedNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.AGENT_PLAN,
                            node_async(steps::agentPlanNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.PARALLEL_EVIDENCE_COLLECTION,
                            node_async(steps::parallelEvidenceNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.RISK_ASSESSMENT,
                            node_async(steps::riskAssessmentNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.RISK_ROUTING,
                            node_async(steps::riskRoutingNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.FINALIZE,
                            node_async(steps::finalizeNode))
                    .addConditionalEdges(
                            START,
                            edge_async(
                                    state ->
                                            state.restoreOnly()
                                                    ? RESTORE_ROUTE
                                                    : EXECUTE_ROUTE),
                            Map.of(
                                    RESTORE_ROUTE,
                                    ExpenseWorkflowNodeNames.RESTORE_WORKFLOW,
                                    EXECUTE_ROUTE,
                                    ExpenseWorkflowNodeNames.LOAD_EXTRACTED))
                    .addEdge(ExpenseWorkflowNodeNames.RESTORE_WORKFLOW, END)
                    .addEdge(
                            ExpenseWorkflowNodeNames.LOAD_EXTRACTED,
                            ExpenseWorkflowNodeNames.AGENT_PLAN)
                    .addEdge(
                            ExpenseWorkflowNodeNames.AGENT_PLAN,
                            ExpenseWorkflowNodeNames.PARALLEL_EVIDENCE_COLLECTION)
                    .addEdge(
                            ExpenseWorkflowNodeNames.PARALLEL_EVIDENCE_COLLECTION,
                            ExpenseWorkflowNodeNames.RISK_ASSESSMENT)
                    .addEdge(
                            ExpenseWorkflowNodeNames.RISK_ASSESSMENT,
                            ExpenseWorkflowNodeNames.RISK_ROUTING)
                    .addEdge(
                            ExpenseWorkflowNodeNames.RISK_ROUTING,
                            ExpenseWorkflowNodeNames.FINALIZE)
                    .addEdge(ExpenseWorkflowNodeNames.FINALIZE, END);
            return new ExpenseWorkflowGraph(stateGraph.compile());
        } catch (GraphStateException exception) {
            throw new IllegalStateException("经费合规审核 LangGraph4j 图构建失败", exception);
        }
    }
}
