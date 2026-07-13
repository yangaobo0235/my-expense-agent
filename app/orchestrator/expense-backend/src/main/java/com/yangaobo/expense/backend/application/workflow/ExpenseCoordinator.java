package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.observability.WorkflowObservability;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExpenseCoordinator {

    private final ExpenseCaseApplicationService caseService;
    private final WorkflowRunRepository runRepository;
    private final WorkflowObservability observability;
    private final ExpenseWorkflowSteps steps;
    private final ExpenseWorkflowGraph graph;

    public ExpenseCoordinator(
            ExpenseCaseApplicationService caseService,
            WorkflowRunRepository runRepository,
            WorkflowObservability observability,
            ExpenseWorkflowSteps steps,
            ExpenseWorkflowGraphFactory graphFactory) {
        this.caseService = caseService;
        this.runRepository = runRepository;
        this.observability = observability;
        this.steps = steps;
        this.graph = graphFactory.graph();
    }

    public ExpenseWorkflowResult analyze(
            UUID caseId, String ownerSubject, ExpenseWorkflowCommand command) {
        ExpenseCase expenseCase = caseService.getOwned(caseId, ownerSubject);
        validateStartState(expenseCase);
        String requestId = required(command.requestId(), "requestId");
        WorkflowRunRepository.WorkflowRun run = runRepository.startOrLoad(caseId, requestId);
        boolean restoreOnly =
                "SUCCEEDED".equals(run.status())
                        && !steps.hasRecoverableFailedEvidenceSteps(run.id());

        return observability
                .workflow(
                        caseId,
                        run.id(),
                        requestId,
                        traceId -> {
                            if (!restoreOnly) {
                                runRepository.attachTraceId(run.id(), traceId);
                            }
                        },
                        () ->
                                executeGraph(
                                        caseId,
                                        ownerSubject,
                                        requestId,
                                        command,
                                        expenseCase,
                                        run,
                                        restoreOnly))
                .value();
    }

    private ExpenseWorkflowResult executeGraph(
            UUID caseId,
            String ownerSubject,
            String requestId,
            ExpenseWorkflowCommand command,
            ExpenseCase expenseCase,
            WorkflowRunRepository.WorkflowRun run,
            boolean restoreOnly) {
        try {
            Map<String, Object> initialState =
                    ExpenseWorkflowGraphState.initial(
                            caseId,
                            run.id(),
                            ownerSubject,
                            requestId,
                            command,
                            expenseCase,
                            restoreOnly);
            return graph.execute(initialState);
        } catch (RuntimeException exception) {
            if (!restoreOnly) {
                runRepository.failRun(
                        run.id(),
                        ExpenseWorkflowSteps.errorCode(exception),
                        ExpenseWorkflowSteps.safeMessage(exception));
                ExpenseCase latest = caseService.getOwned(caseId, ownerSubject);
                if (latest.status() != ExpenseCaseStatus.FAILED && !latest.status().isTerminal()) {
                    caseService.fail(
                            caseId,
                            "COORDINATOR",
                            ExpenseWorkflowSteps.safeMessage(exception));
                }
            }
            throw exception;
        }
    }

    private static void validateStartState(ExpenseCase expenseCase) {
        if (expenseCase.status().isTerminal()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.INVALID_STATE_TRANSITION,
                    "已完成审批的申请不能重新启动审核工作流");
        }
        if (expenseCase.status() != ExpenseCaseStatus.EXTRACTED
                && expenseCase.status() != ExpenseCaseStatus.WAITING_HUMAN
                && expenseCase.status() != ExpenseCaseStatus.FAILED) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.INVALID_STATE_TRANSITION,
                    "只有已完成提取的案例才能启动完整审核工作流");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.VALIDATION_FAILED, field + "不能为空");
        }
        return value.trim();
    }
}
