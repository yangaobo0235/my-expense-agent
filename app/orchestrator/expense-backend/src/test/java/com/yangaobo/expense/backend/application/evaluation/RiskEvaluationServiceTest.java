package com.yangaobo.expense.backend.application.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.domain.risk.DeterministicRiskEngine;
import com.yangaobo.expense.backend.domain.risk.RiskSignalCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RiskEvaluationServiceTest {

    private static final String DATASET =
            "classpath:evaluation/cases/risk-golden-v3.json";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void evaluatesExpandedHardCaseDataset() {
        RiskEvaluationService service =
                new RiskEvaluationService(
                        objectMapper,
                        new DeterministicRiskEngine(),
                        Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC),
                        DATASET);

        RiskEvaluationReport report = service.evaluate();

        assertEquals("risk-golden-v3", report.datasetVersion());
        assertEquals(300, report.caseCount());
        assertEquals(272.0 / 300.0, report.metrics().riskLevelAccuracy(), 1e-12);
        assertEquals(280.0 / 300.0, report.metrics().routingAccuracy(), 1e-12);
        assertEquals(57.0 / 65.0, report.metrics().highRiskRecall(), 1e-12);
        assertTrue(report.failures().size() >= 20);
        assertEquals(
                5,
                report.categoryCounts().get("票据提示注入攻击"));
        assertTrue(report.failures().stream()
                .map(RiskEvaluationReport.Failure::caseId)
                .anyMatch(id -> id.startsWith("confidence-boundary-")));
        assertTrue(report.failures().stream()
                .map(RiskEvaluationReport.Failure::caseId)
                .anyMatch(id -> id.startsWith("high-policy-")));

        System.out.printf(
                "risk-eval cases=%d levelAccuracy=%.4f routingAccuracy=%.4f highRiskRecall=%.4f failures=%d%n",
                report.caseCount(),
                report.metrics().riskLevelAccuracy(),
                report.metrics().routingAccuracy(),
                report.metrics().highRiskRecall(),
                report.failures().size());
    }

    @Test
    void keepsDatasetIdsUniqueAndCoversHardCaseCategories() {
        RiskEvaluationDataset dataset =
                EvaluationDatasetLoader.load(
                        objectMapper,
                        DATASET,
                        "risk",
                        RiskEvaluationDataset.class,
                        "无法读取风险评测数据集");

        Set<String> ids = new HashSet<>();
        Set<String> knownSignals =
                java.util.Arrays.stream(RiskSignalCode.values())
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet());

        assertEquals(300, dataset.cases().size());
        assertTrue(dataset.cases().stream().allMatch(testCase -> ids.add(testCase.id())));
        assertTrue(
                dataset.cases().stream()
                        .flatMap(testCase -> testCase.expectedSignals().stream())
                        .allMatch(knownSignals::contains));
        assertEquals(
                10,
                dataset.cases().stream()
                        .filter(testCase -> testCase.category().equals("项目预算不足"))
                        .count());
        assertEquals(
                10,
                dataset.cases().stream()
                        .filter(testCase -> testCase.category().equals("制度证据缺失"))
                        .count());
        assertEquals(
                10,
                dataset.cases().stream()
                        .filter(testCase -> testCase.category().equals("复合风险场景"))
                        .count());
        assertEquals(
                10,
                dataset.cases().stream()
                        .filter(testCase -> testCase.category().equals("严重低置信度人工复核"))
                        .count());
        assertEquals(
                20,
                dataset.cases().stream()
                        .filter(testCase -> testCase.category().equals("金额容差边界"))
                        .count());
        assertEquals(
                20,
                dataset.cases().stream()
                        .filter(testCase -> testCase.category().equals("高风险单项策略"))
                        .count());
    }
}
