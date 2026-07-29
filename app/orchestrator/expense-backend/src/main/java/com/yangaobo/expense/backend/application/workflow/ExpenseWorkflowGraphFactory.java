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
    private static final String MISSING_ROUTE = "MISSING";
    private static final String RISK_ROUTE = "RISK";
    private static final String LOW_ROUTE = "LOW";
    private static final String COLLEGE_ROUTE = "COLLEGE";
    private static final String FINANCE_ROUTE = "FINANCE";
    private static final String MORE_INFO_ROUTE = "MORE_INFO";
    private static final String DEPENDENCY_ROUTE = "DEPENDENCY";

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
                            ExpenseWorkflowNodeNames.EXECUTION_POLICY,
                            node_async(steps::executionPolicyNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.PARALLEL_EVIDENCE_COLLECTION,
                            node_async(steps::parallelEvidenceNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.EVIDENCE_QUALITY_GATE,
                            node_async(steps::evidenceQualityNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.RISK_ASSESSMENT,
                            node_async(steps::riskAssessmentNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.RISK_ROUTING,
                            node_async(steps::riskRoutingNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.CREATE_MORE_INFO_TASK,
                            node_async(steps::createMoreInfoTaskNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.GENERATE_REVIEW_SUMMARY,
                            node_async(steps::generateReviewSummaryNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.VERIFY_SUMMARY_REFERENCES,
                            node_async(steps::verifySummaryReferencesNode))
                    .addNode(
                            ExpenseWorkflowNodeNames.CREATE_REVIEW_TASK,
                            node_async(steps::createReviewTaskNode))
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
                            ExpenseWorkflowNodeNames.EXECUTION_POLICY)
                    .addEdge(
                            ExpenseWorkflowNodeNames.EXECUTION_POLICY,
                            ExpenseWorkflowNodeNames.PARALLEL_EVIDENCE_COLLECTION)
                    .addEdge(
                            ExpenseWorkflowNodeNames.PARALLEL_EVIDENCE_COLLECTION,
                            ExpenseWorkflowNodeNames.EVIDENCE_QUALITY_GATE)
                    .addConditionalEdges(
                            ExpenseWorkflowNodeNames.EVIDENCE_QUALITY_GATE,
                            edge_async(state -> state.evidenceQuality().quality()
                                    == EvidenceQuality.MISSING_MATERIALS ? MISSING_ROUTE : RISK_ROUTE),
                            Map.of(
                                    MISSING_ROUTE, ExpenseWorkflowNodeNames.CREATE_MORE_INFO_TASK,
                                    RISK_ROUTE, ExpenseWorkflowNodeNames.RISK_ASSESSMENT))
                    .addEdge(
                            ExpenseWorkflowNodeNames.RISK_ASSESSMENT,
                            ExpenseWorkflowNodeNames.RISK_ROUTING)
                    .addConditionalEdges(
                            ExpenseWorkflowNodeNames.RISK_ROUTING,
                            edge_async(state -> route(state.routingDecision().action())),
                            Map.of(
                                    LOW_ROUTE, ExpenseWorkflowNodeNames.FINALIZE,
                                    MORE_INFO_ROUTE, ExpenseWorkflowNodeNames.CREATE_MORE_INFO_TASK,
                                    COLLEGE_ROUTE, ExpenseWorkflowNodeNames.CREATE_REVIEW_TASK,
                                    DEPENDENCY_ROUTE, ExpenseWorkflowNodeNames.CREATE_REVIEW_TASK,
                                    FINANCE_ROUTE, ExpenseWorkflowNodeNames.GENERATE_REVIEW_SUMMARY))
                    .addEdge(
                            ExpenseWorkflowNodeNames.GENERATE_REVIEW_SUMMARY,
                            ExpenseWorkflowNodeNames.VERIFY_SUMMARY_REFERENCES)
                    .addEdge(
                            ExpenseWorkflowNodeNames.VERIFY_SUMMARY_REFERENCES,
                            ExpenseWorkflowNodeNames.CREATE_REVIEW_TASK)
                    .addEdge(ExpenseWorkflowNodeNames.CREATE_MORE_INFO_TASK, END)
                    .addEdge(ExpenseWorkflowNodeNames.CREATE_REVIEW_TASK, END)
                    .addEdge(ExpenseWorkflowNodeNames.FINALIZE, END);
            return new ExpenseWorkflowGraph(stateGraph.compile());
        } catch (GraphStateException exception) {
            throw new IllegalStateException("经费合规审核 LangGraph4j 图构建失败", exception);
        }
    }

    private static String route(RiskRoutingAction action) {
        return switch (action) {
            case LOW_RISK_PATH -> LOW_ROUTE;
            case REQUEST_MORE_INFO -> MORE_INFO_ROUTE;
            case COLLEGE_REVIEW -> COLLEGE_ROUTE;
            case FINANCE_REVIEW -> FINANCE_ROUTE;
            case DEPENDENCY_REVIEW -> DEPENDENCY_ROUTE;
        };
    }
}
