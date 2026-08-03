package com.yangaobo.expense.backend.application.extraction;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExtractionRepairPolicy {

    public static final int MAX_REPAIR_ATTEMPTS = 1;

    public boolean shouldRepair(List<ExtractionValidationError> errors, int repairAttempts) {
        return repairAttempts < MAX_REPAIR_ATTEMPTS
                && errors != null
                && !errors.isEmpty()
                && errors.stream().allMatch(ExtractionValidationError::repairable);
    }
}
