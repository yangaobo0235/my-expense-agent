package com.yangaobo.expense.backend.application.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.policy.PolicyRetrievalService;
import com.yangaobo.expense.backend.application.policy.PolicySearchQuery;
import com.yangaobo.expense.backend.domain.repository.PolicySearchMatch;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PolicyRagEvaluationService {

    private final ObjectMapper objectMapper;
    private final PolicyRetrievalService policyService;
    private final Clock clock;
    private final String datasetLocation;

    public PolicyRagEvaluationService(
            ObjectMapper objectMapper,
            PolicyRetrievalService policyService,
            Clock clock,
            @Value("${expense.evaluation.policy-rag-dataset:classpath:evaluation/cases/policy-rag-golden-v1.json}")
                    String datasetLocation) {
        this.objectMapper = objectMapper;
        this.policyService = policyService;
        this.clock = clock;
        this.datasetLocation = datasetLocation;
    }

    public PolicyRagEvaluationReport evaluate() {
        PolicyRagEvaluationDataset dataset = dataset();
        int answerable = 0;
        int hitAt5 = 0;
        int policyHits = 0;
        int sectionHits = 0;
        int noAnswer = 0;
        int noAnswerCorrect = 0;
        int relevantReturned = 0;
        int totalReturned = 0;
        long totalLatencyMs = 0;
        boolean injectionDefensePassed = true;
        List<PolicyRagEvaluationReport.Failure> failures = new ArrayList<>();
        for (PolicyRagEvaluationDataset.PolicyRagCase testCase : dataset.cases()) {
            long started = System.nanoTime();
            List<PolicySearchMatch> matches =
                    policyService.search(
                            new PolicySearchQuery(
                                    testCase.query(),
                                    testCase.category(),
                                    testCase.region(),
                                    testCase.applicantType(),
                                    testCase.expenseDate(),
                                    5,
                                    0.25));
            totalLatencyMs += java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
            totalReturned += matches.size();
            boolean policyHit =
                    matches.stream()
                            .anyMatch(match -> match.policyCode().equals(testCase.expectedPolicyCode()));
            boolean sectionHit =
                    matches.stream()
                            .anyMatch(match -> testCase.expectedSections().contains(match.section()));
            long relevant =
                    matches.stream()
                            .filter(
                                    match ->
                                            match.policyCode().equals(testCase.expectedPolicyCode())
                                                    || testCase.expectedSections().contains(match.section()))
                            .count();
            relevantReturned += (int) relevant;
            if (testCase.expectedAnswerable()) {
                answerable++;
                policyHits += policyHit ? 1 : 0;
                sectionHits += sectionHit ? 1 : 0;
                hitAt5 += policyHit && sectionHit ? 1 : 0;
                if (!(policyHit && sectionHit)) {
                    failures.add(failure(testCase, matches, "EXPECTED_POLICY_OR_SECTION_MISSED"));
                }
            } else {
                noAnswer++;
                if (matches.isEmpty()) {
                    noAnswerCorrect++;
                } else {
                    failures.add(failure(testCase, matches, "NO_ANSWER_CASE_RETURNED_CONTEXT"));
                }
            }
            if (testCase.injectionCase()) {
                injectionDefensePassed =
                        injectionDefensePassed
                                && matches.stream()
                                        .noneMatch(
                                                match ->
                                                        match.content().contains("submit_fund_posting")
                                                                || match.content().contains("直接批准"));
            }
        }
        int count = dataset.cases().size();
        return new PolicyRagEvaluationReport(
                dataset.datasetVersion(),
                clock.instant(),
                count,
                new PolicyRagEvaluationReport.Metrics(
                        ratio(hitAt5, answerable),
                        ratio(relevantReturned, totalReturned),
                        ratio(policyHits, answerable),
                        ratio(sectionHits, answerable),
                        ratio(noAnswerCorrect, noAnswer),
                        injectionDefensePassed,
                        ratio((int) totalLatencyMs, count)),
                List.copyOf(failures));
    }

    private PolicyRagEvaluationDataset dataset() {
        return EvaluationDatasetLoader.load(
                objectMapper,
                datasetLocation,
                "policyRag",
                PolicyRagEvaluationDataset.class,
                "无法读取制度 RAG 评测集");
    }

    private static PolicyRagEvaluationReport.Failure failure(
            PolicyRagEvaluationDataset.PolicyRagCase testCase,
            List<PolicySearchMatch> matches,
            String reason) {
        return new PolicyRagEvaluationReport.Failure(
                testCase.caseId(),
                testCase.query(),
                testCase.expectedPolicyCode(),
                testCase.expectedSections(),
                matches.stream()
                        .map(
                                match ->
                                        Map.<String, Object>of(
                                                "policyCode",
                                                match.policyCode(),
                                                "section",
                                                match.section(),
                                                "chunkId",
                                                match.chunkId().toString(),
                                                "score",
                                                match.score()))
                        .toList(),
                reason);
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
