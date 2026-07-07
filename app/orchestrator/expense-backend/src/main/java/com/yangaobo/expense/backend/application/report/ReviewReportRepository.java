package com.yangaobo.expense.backend.application.report;

import java.util.Optional;
import java.util.UUID;

public interface ReviewReportRepository {

    ReviewReport save(ReviewReport report);

    Optional<ReviewReport> latest(UUID caseId);
}
