package com.yangaobo.expense.backend.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.agents.mcp.ApprovedMcpWriteRequest;
import com.yangaobo.expense.agents.mcp.ExpenseMcpGateway;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard.GuardMode;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class ApprovedMcpWriteService {

    private final ExpenseMcpGateway gateway;
    private final ExpenseCaseApplicationService caseService;
    private final ObjectMapper objectMapper;
    private final AgentInputGuard inputGuard;
    private final McpRetryExecutor retryExecutor;

    public ApprovedMcpWriteService(
            ExpenseMcpGateway gateway,
            ExpenseCaseApplicationService caseService,
            ObjectMapper objectMapper,
            McpRetryExecutor retryExecutor) {
        this(
                gateway,
                caseService,
                objectMapper,
                new AgentInputGuard(new com.yangaobo.expense.backend.application.governance.SensitiveDataMasker()),
                retryExecutor);
    }

    public ApprovedMcpWriteService(
            ExpenseMcpGateway gateway,
            ExpenseCaseApplicationService caseService,
            ObjectMapper objectMapper,
            AgentInputGuard inputGuard,
            McpRetryExecutor retryExecutor) {
        this.gateway = gateway;
        this.caseService = caseService;
        this.objectMapper = objectMapper;
        this.inputGuard = inputGuard;
        this.retryExecutor = retryExecutor;
    }

    public McpWriteResult submitReimbursement(
            UUID caseId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference) {
        ExpenseCase expenseCase = requireApproved(caseId);
        if (amount == null
                || amount.signum() < 0
                || amount.compareTo(
                                expenseCase.claimedAmount().amount())
                        > 0) {
            throw validation("报销金额必须处于 0 到申报金额之间");
        }
        return execute(
                "submit_reimbursement",
                Map.of(
                        "requestId",
                        required(requestId, "requestId"),
                        "caseId",
                        caseId.toString(),
                        "amount",
                        amount,
                        "currency",
                        required(currency, "currency")),
                requestId,
                actorSubject,
                approvalReference);
    }

    public McpWriteResult submitPayment(
            UUID caseId,
            UUID reimbursementId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference) {
        ExpenseCase expenseCase = requireApproved(caseId);
        if (reimbursementId == null) {
            throw validation("reimbursementId不能为空");
        }
        if (amount == null
                || amount.signum() < 0
                || amount.compareTo(
                                expenseCase.claimedAmount().amount())
                        > 0) {
            throw validation("付款金额必须处于 0 到申报金额之间");
        }
        return execute(
                "submit_payment",
                Map.of(
                        "requestId",
                        required(requestId, "requestId"),
                        "reimbursementId",
                        reimbursementId.toString(),
                        "amount",
                        amount,
                        "currency",
                        required(currency, "currency")),
                requestId,
                actorSubject,
                approvalReference);
    }

    public McpWriteResult saveReviewEvidence(
            UUID caseId,
            String evidenceType,
            String contentHash,
            String requestId,
            String actorSubject,
            UUID workflowRunId) {
        if (caseId == null || workflowRunId == null) {
            throw validation("caseId 和 workflowRunId 不能为空");
        }
        ExpenseCase expenseCase = caseService.getById(caseId);
        if (expenseCase.status() == ExpenseCaseStatus.UPLOADED
                || expenseCase.status()
                        == ExpenseCaseStatus.EXTRACTING) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.INVALID_STATE_TRANSITION,
                    "票据提取完成前不能保存审核证据");
        }
        return execute(
                "save_review_evidence",
                Map.of(
                        "requestId",
                        required(requestId, "requestId"),
                        "caseId",
                        caseId.toString(),
                        "evidenceType",
                        required(evidenceType, "evidenceType"),
                        "contentHash",
                        required(contentHash, "contentHash")),
                requestId,
                actorSubject,
                "workflow-run:" + workflowRunId);
    }

    private ExpenseCase requireApproved(UUID caseId) {
        if (caseId == null) {
            throw validation("caseId不能为空");
        }
        ExpenseCase expenseCase = caseService.getById(caseId);
        if (expenseCase.status() != ExpenseCaseStatus.APPROVED) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.INVALID_STATE_TRANSITION,
                    "只有已批准的费用案例才能执行报销或付款 Tool");
        }
        return expenseCase;
    }

    private McpWriteResult execute(
            String toolName,
            Map<String, Object> arguments,
            String requestId,
            String actorSubject,
            String approvalReference) {
        Map<String, Object> guardedArguments =
                inputGuard.inspectMap("mcp-write:" + toolName, arguments, GuardMode.BLOCK);
        ToolExecutionResult result =
                retryExecutor.execute(
                        toolName,
                        () ->
                                gateway.executeApprovedWrite(
                                                new ApprovedMcpWriteRequest(
                                                toolName,
                                                write(guardedArguments),
                                                requestId,
                                                actorSubject,
                                                approvalReference)));
        return new McpWriteResult(
                toolName,
                !result.isError(),
                result.resultText());
    }

    private String write(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("MCP Tool 参数序列化失败", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw validation(field + "不能为空");
        }
        return value.trim();
    }

    private static ExpenseFlowException validation(String message) {
        return new ExpenseFlowException(
                ExpenseFlowErrorCode.VALIDATION_FAILED, message);
    }

    public record McpWriteResult(
            String toolName, boolean success, String resultText) {}
}
