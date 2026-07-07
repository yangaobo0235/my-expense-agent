package com.yangaobo.expense.backend.application.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.observability.ModelCallRecorder;
import com.yangaobo.expense.backend.application.workflow.CaseEvidenceService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewReportService {

    private final ExpenseCaseApplicationService caseService;
    private final CaseEvidenceService evidenceService;
    private final ReviewReportAgent agent;
    private final ReviewReportRepository repository;
    private final ModelCallRecorder modelCallRecorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReviewReportService(
            ExpenseCaseApplicationService caseService,
            CaseEvidenceService evidenceService,
            ReviewReportAgent agent,
            ReviewReportRepository repository,
            ModelCallRecorder modelCallRecorder,
            ObjectMapper objectMapper,
            Clock clock) {
        this.caseService = caseService;
        this.evidenceService = evidenceService;
        this.agent = agent;
        this.repository = repository;
        this.modelCallRecorder = modelCallRecorder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ReviewReport> latest(UUID caseId, String subject, boolean privileged) {
        authorize(caseId, subject, privileged);
        return repository.latest(caseId);
    }

    @Transactional
    public ReviewReport generate(UUID caseId, String subject, boolean privileged) {
        ExpenseCase expenseCase = authorize(caseId, subject, privileged);
        CaseEvidenceService.CaseEvidence evidence =
                evidenceService.get(caseId, subject, privileged);
        long startedNanos = System.nanoTime();
        ReviewReportAgent.DraftResult draft = agent.draft(expenseCase, evidence, clock.instant());
        ReviewReport report = draft.report();
        ReviewReport saved = repository.save(report);
        if (draft.completion() == null) {
            modelCallRecorder.failed(
                    caseId,
                    evidence.run() == null ? null : evidence.run().id(),
                    "REVIEW_REPORT_GENERATION",
                    draft.prompt().modelName(),
                    draft.prompt().version(),
                    draft.prompt().content(),
                    json(Map.of("case", expenseCase.id(), "evidenceSteps", evidence.steps().size())),
                    java.time.Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                    0,
                    draft.fallbackReason() == null ? "MODEL_FALLBACK" : draft.fallbackReason());
        } else {
            modelCallRecorder.succeeded(
                    caseId,
                    evidence.run() == null ? null : evidence.run().id(),
                    "REVIEW_REPORT_GENERATION",
                    draft.prompt().modelName(),
                    draft.prompt().version(),
                    draft.prompt().content(),
                    json(Map.of("case", expenseCase.id(), "evidenceSteps", evidence.steps().size())),
                    json(saved),
                    draft.completion().promptTokens(),
                    draft.completion().completionTokens(),
                    draft.completion().latencyMs(),
                    draft.completion().retryCount());
        }
        return saved;
    }

    private ExpenseCase authorize(UUID caseId, String subject, boolean privileged) {
        return privileged ? caseService.getById(caseId) : caseService.getOwned(caseId, subject);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审核报告上下文序列化失败", exception);
        }
    }

    private static int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }
}
