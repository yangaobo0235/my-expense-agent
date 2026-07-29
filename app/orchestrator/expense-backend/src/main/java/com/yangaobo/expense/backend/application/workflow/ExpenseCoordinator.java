package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExpenseCoordinator {

    private final ExpenseCaseApplicationService caseService;
    private final WorkflowRunRepository runRepository;
    private final ExpenseWorkflowSteps steps;
    private final ExpenseWorkflowGraph graph;

    public ExpenseCoordinator(
            ExpenseCaseApplicationService caseService,
            WorkflowRunRepository runRepository,
            ExpenseWorkflowSteps steps,
            ExpenseWorkflowGraphFactory graphFactory) {
        this.caseService = caseService;
        this.runRepository = runRepository;
        this.steps = steps;
        this.graph = graphFactory.graph();
    }

    public ExpenseWorkflowResult analyze(
            UUID caseId, String ownerSubject, ExpenseWorkflowCommand command) {
        ExpenseCase expenseCase = caseService.getOwned(caseId, ownerSubject);
        validateStartState(expenseCase);
        String requestId = required(command.requestId(), "requestId");
        int documentVersion = command.documentVersion() == null
                ? runRepository.currentDocumentVersion(caseId)
                : command.documentVersion();
        validateReviewAgain(caseId, command, documentVersion);
        WorkflowRunRepository.WorkflowRun run = runRepository.startOrLoad(
                caseId, requestId, command.commandType(), documentVersion,
                command.previousRunId(), command.reopenReason());
        boolean restoreOnly =
                "SUCCEEDED".equals(run.status())
                        && !steps.hasRecoverableFailedEvidenceSteps(run.id());

        return executeGraph(
                caseId,
                ownerSubject,
                requestId,
                command,
                expenseCase,
                run,
                restoreOnly);
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
                            restoreOnly,
                            run);
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
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.INVALID_STATE_TRANSITION,
                    "已完成审批的申请不能重新启动审核工作流");
        }
        if (expenseCase.status() != ExpenseCaseStatus.EXTRACTED
                && expenseCase.status() != ExpenseCaseStatus.WAITING_HUMAN
                && expenseCase.status() != ExpenseCaseStatus.FAILED) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.INVALID_STATE_TRANSITION,
                    "只有已完成提取的案例才能启动完整审核工作流");
        }
    }

    private void validateReviewAgain(
            UUID caseId, ExpenseWorkflowCommand command, int documentVersion) {
        if (command.commandType() != WorkflowCommandType.REVIEW_AGAIN) {
            return;
        }
        if (command.previousRunId() == null || command.reopenReason() == null
                || command.reopenReason().isBlank()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED,
                    "REVIEW_AGAIN 必须提供 previousRunId 和 reopenReason");
        }
        WorkflowRunRepository.WorkflowRunDetail previous = runRepository.findRun(command.previousRunId())
                .filter(item -> item.caseId().equals(caseId))
                .orElseThrow(() -> new MyExpenseAgentException(
                        MyExpenseAgentErrorCode.VALIDATION_FAILED, "previousRunId 不属于当前案例"));
        if (documentVersion <= previous.documentVersion()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED,
                    "重新审核必须使用更高的文档版本");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED, field + "不能为空");
        }
        return value.trim();
    }
}
