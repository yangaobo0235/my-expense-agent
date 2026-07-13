package com.yangaobo.expense.backend.application.workflow;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ExpenseContextGateway {

    ApplicantContext applicantContext(
            String applicantId,
            String projectCode);

    ProjectBudget projectBudget(String applicantId, String projectCode);

    ReimbursementHistory reimbursementHistory(String applicantId);

    DuplicateCheck duplicateCheck(
            UUID currentCaseId,
            List<String> documentSha256);

    record ApplicantContext(
            String applicantId,
            String projectCode,
            String applicantType,
            String region,
            List<String> reimbursementAccounts,
            String source,
            boolean dependencyFailure,
            String failureReason)
            implements Serializable {

        public ApplicantContext {
            reimbursementAccounts =
                    reimbursementAccounts == null
                            ? List.of()
                            : List.copyOf(reimbursementAccounts);
        }
    }

    record ProjectBudget(
            String applicantId,
            String projectCode,
            BigDecimal total,
            BigDecimal available,
            String currency,
            long version,
            Instant updatedAt,
            String source,
            boolean dependencyFailure,
            String failureReason)
            implements Serializable {}

    record ReimbursementHistory(
            List<Map<String, Object>> items,
            String source,
            boolean dependencyFailure,
            String failureReason)
            implements Serializable {

        public ReimbursementHistory {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record DuplicateCheck(
            boolean duplicate,
            List<String> duplicateSha256,
            Map<String, Object> evidence,
            String source,
            boolean dependencyFailure,
            String failureReason)
            implements Serializable {

        public DuplicateCheck {
            duplicateSha256 =
                    duplicateSha256 == null
                            ? List.of()
                            : List.copyOf(duplicateSha256);
            evidence =
                    evidence == null ? Map.of() : Map.copyOf(evidence);
        }
    }
}
