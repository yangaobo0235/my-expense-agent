package com.yangaobo.expense.backend.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.settlement.ApprovedExpenseWriter;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class McpApprovedExpenseWriter implements ApprovedExpenseWriter {

    private final ApprovedMcpWriteService writeService;
    private final ObjectMapper objectMapper;

    public McpApprovedExpenseWriter(
            ApprovedMcpWriteService writeService,
            ObjectMapper objectMapper) {
        this.writeService = writeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public WriteResult submitReimbursement(
            UUID caseId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference) {
        var result =
                writeService.submitReimbursement(
                        caseId,
                        amount,
                        currency,
                        requestId,
                        actorSubject,
                        approvalReference);
        return new WriteResult(result.success(), parse(result.resultText()));
    }

    @Override
    public WriteResult submitPayment(
            UUID caseId,
            UUID reimbursementId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference) {
        var result =
                writeService.submitPayment(
                        caseId,
                        reimbursementId,
                        amount,
                        currency,
                        requestId,
                        actorSubject,
                        approvalReference);
        return new WriteResult(result.success(), parse(result.resultText()));
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("写 Tool 返回的不是合法 JSON", exception);
        }
    }
}
