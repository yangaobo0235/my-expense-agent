package com.yangaobo.expense.backend.application.workflow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ExpenseContextGateway {

    EmployeeContext employeeContext(
            String employeeId,
            String fallbackRegion,
            String fallbackEmployeeGrade);

    DuplicateCheck duplicateCheck(
            UUID currentCaseId,
            List<String> documentSha256,
            boolean fallbackDuplicate);

    record EmployeeContext(
            String employeeId,
            String departmentCode,
            String employeeGrade,
            String region,
            List<String> paymentMethods,
            String source,
            boolean dependencyFailure,
            String failureReason) {

        public EmployeeContext {
            paymentMethods =
                    paymentMethods == null
                            ? List.of()
                            : List.copyOf(paymentMethods);
        }
    }

    record DuplicateCheck(
            boolean duplicate,
            List<String> duplicateSha256,
            Map<String, Object> evidence,
            String source,
            boolean dependencyFailure,
            String failureReason) {

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
