package com.yangaobo.expense.backend.application.report;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewReport(
        UUID id,
        UUID caseId,
        String summary,
        List<String> riskExplanation,
        List<PolicyCitation> policyCitations,
        List<String> humanReviewHints,
        List<String> limitations,
        String modelName,
        String promptVersion,
        Instant createdAt) {

    public ReviewReport {
        riskExplanation = riskExplanation == null ? List.of() : List.copyOf(riskExplanation);
        policyCitations = policyCitations == null ? List.of() : List.copyOf(policyCitations);
        humanReviewHints = humanReviewHints == null ? List.of() : List.copyOf(humanReviewHints);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public record PolicyCitation(String policyCode, String section, String chunkId) {}
}
