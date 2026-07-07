package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.domain.repository.PolicySearchMatch;
import java.time.LocalDate;
import java.util.UUID;

public record PolicySearchResponse(
        UUID policyId,
        String policyCode,
        String policyName,
        String policyVersion,
        String category,
        String region,
        String employeeGrade,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String sourceUri,
        UUID chunkId,
        int chunkIndex,
        String section,
        String content,
        double score,
        String citation) {

    static PolicySearchResponse from(PolicySearchMatch match) {
        return new PolicySearchResponse(
                match.policyId(),
                match.policyCode(),
                match.policyName(),
                match.policyVersion(),
                match.category(),
                match.region(),
                match.employeeGrade(),
                match.effectiveFrom(),
                match.effectiveTo(),
                match.sourceUri(),
                match.chunkId(),
                match.chunkIndex(),
                match.section(),
                match.content(),
                match.score(),
                "%s（版本 %s，%s）"
                        .formatted(
                                match.policyName(),
                                match.policyVersion(),
                                match.section()));
    }
}
