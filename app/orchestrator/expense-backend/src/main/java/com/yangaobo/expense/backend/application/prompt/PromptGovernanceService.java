package com.yangaobo.expense.backend.application.prompt;

import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromptGovernanceService {

    private final PromptTemplateRepository repository;
    private final PromptEvaluationGate evaluationGate;
    private final Clock clock;

    public PromptGovernanceService(
            PromptTemplateRepository repository,
            PromptEvaluationGate evaluationGate,
            Clock clock) {
        this.repository = repository;
        this.evaluationGate = evaluationGate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PromptTemplate> list(String promptKey) {
        return repository.list(promptKey);
    }

    @Transactional(readOnly = true)
    public PromptTemplate get(UUID id) {
        return template(id);
    }

    @Transactional
    public PromptTemplate createDraft(
            String promptKey,
            String version,
            String name,
            String description,
            String content,
            Map<String, Object> variableSchema,
            String modelName,
            BigDecimal temperature,
            int maxTokens,
            String actor) {
        if (repository.findByKeyAndVersion(required(promptKey, "promptKey"), required(version, "version")).isPresent()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.VALIDATION_FAILED, "Prompt key/version 已存在");
        }
        Instant now = clock.instant();
        PromptTemplate template =
                new PromptTemplate(
                        UUID.randomUUID(),
                        promptKey.trim(),
                        version.trim(),
                        required(name, "name"),
                        description == null ? "" : description.trim(),
                        required(content, "content"),
                        variableSchema,
                        required(modelName, "modelName"),
                        temperature == null ? BigDecimal.ZERO : temperature,
                        Math.max(1, maxTokens),
                        PromptStatus.DRAFT,
                        ModelHash.sha256(content),
                        actor,
                        actor,
                        null,
                        now,
                        now,
                        null,
                        null,
                        null);
        repository.save(template);
        repository.appendAudit(promptKey, version, "CREATED", actor, Map.of("status", "DRAFT"));
        return template;
    }

    @Transactional
    public PromptTemplate updateDraft(
            UUID id,
            String name,
            String description,
            String content,
            Map<String, Object> variableSchema,
            String modelName,
            BigDecimal temperature,
            int maxTokens,
            String actor) {
        PromptTemplate current = template(id);
        requireStatus(current, PromptStatus.DRAFT, PromptStatus.REJECTED);
        PromptTemplate updated =
                new PromptTemplate(
                        current.id(),
                        current.promptKey(),
                        current.version(),
                        required(name, "name"),
                        description == null ? "" : description.trim(),
                        required(content, "content"),
                        variableSchema,
                        required(modelName, "modelName"),
                        temperature == null ? BigDecimal.ZERO : temperature,
                        Math.max(1, maxTokens),
                        PromptStatus.DRAFT,
                        ModelHash.sha256(content),
                        current.createdBy(),
                        actor,
                        null,
                        current.createdAt(),
                        clock.instant(),
                        null,
                        null,
                        current.replacedVersion());
        repository.update(updated);
        repository.appendAudit(updated.promptKey(), updated.version(), "UPDATED", actor, Map.of("status", "DRAFT"));
        return updated;
    }

    @Transactional
    public PromptChangeRequest submit(UUID templateId, String diffSummary, String actor) {
        PromptTemplate current = template(templateId);
        requireStatus(current, PromptStatus.DRAFT, PromptStatus.REJECTED);
        Map<String, Object> report = evaluationGate.evaluate(current);
        PromptTemplate inReview =
                withStatus(current, PromptStatus.IN_REVIEW, actor, null, null, null, clock.instant());
        repository.update(inReview);
        PromptChangeRequest request =
                new PromptChangeRequest(
                        UUID.randomUUID(),
                        templateId,
                        PromptChangeRequestType.UPDATE,
                        PromptChangeRequestStatus.PENDING,
                        diffSummary == null ? "" : diffSummary.trim(),
                        String.valueOf(report.getOrDefault("riskLevel", "LOW")),
                        report,
                        "",
                        actor,
                        null,
                        clock.instant(),
                        null);
        repository.saveChangeRequest(request);
        repository.appendAudit(current.promptKey(), current.version(), "SUBMITTED", actor, report);
        return request;
    }

    @Transactional
    public PromptChangeRequest approve(UUID requestId, String comment, String actor) {
        PromptChangeRequest request = changeRequest(requestId);
        if (request.status() != PromptChangeRequestStatus.PENDING) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.INVALID_STATE_TRANSITION, "审批单不是待审批状态");
        }
        if (sameActor(request.submittedBy(), actor)) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, "Prompt 提交人不能审批自己的变更");
        }
        requireComment(comment, "审批");
        if (!evaluationGate.passed(request.evaluationReport())) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, "Prompt 自动门禁未通过，不能审批");
        }
        PromptTemplate current = template(request.promptTemplateId());
        requireStatus(current, PromptStatus.IN_REVIEW);
        Instant now = clock.instant();
        PromptTemplate approved =
                new PromptTemplate(
                        current.id(),
                        current.promptKey(),
                        current.version(),
                        current.name(),
                        current.description(),
                        current.content(),
                        current.variableSchema(),
                        current.modelName(),
                        current.temperature(),
                        current.maxTokens(),
                        PromptStatus.APPROVED,
                        current.promptHash(),
                        current.createdBy(),
                        actor,
                        actor,
                        current.createdAt(),
                        now,
                        now,
                        null,
                        current.replacedVersion());
        repository.update(approved);
        PromptChangeRequest updated =
                new PromptChangeRequest(
                        request.id(),
                        request.promptTemplateId(),
                        request.requestType(),
                        PromptChangeRequestStatus.APPROVED,
                        request.diffSummary(),
                        request.riskLevel(),
                        request.evaluationReport(),
                        comment == null ? "" : comment.trim(),
                        request.submittedBy(),
                        actor,
                        request.submittedAt(),
                        now);
        repository.updateChangeRequest(updated);
        repository.appendAudit(approved.promptKey(), approved.version(), "APPROVED", actor, Map.of("comment", updated.reviewComment()));
        return updated;
    }

    @Transactional
    public PromptChangeRequest reject(UUID requestId, String comment, String actor) {
        PromptChangeRequest request = changeRequest(requestId);
        if (request.status() != PromptChangeRequestStatus.PENDING) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.INVALID_STATE_TRANSITION, "审批单不是待审批状态");
        }
        if (sameActor(request.submittedBy(), actor)) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, "Prompt 提交人不能驳回自己的变更");
        }
        requireComment(comment, "驳回");
        PromptTemplate current = template(request.promptTemplateId());
        repository.update(withStatus(current, PromptStatus.REJECTED, actor, null, null, null, clock.instant()));
        PromptChangeRequest updated =
                new PromptChangeRequest(
                        request.id(),
                        request.promptTemplateId(),
                        request.requestType(),
                        PromptChangeRequestStatus.REJECTED,
                        request.diffSummary(),
                        request.riskLevel(),
                        request.evaluationReport(),
                        comment == null ? "" : comment.trim(),
                        request.submittedBy(),
                        actor,
                        request.submittedAt(),
                        clock.instant());
        repository.updateChangeRequest(updated);
        repository.appendAudit(current.promptKey(), current.version(), "REJECTED", actor, Map.of("comment", updated.reviewComment()));
        return updated;
    }

    @Transactional
    public PromptTemplate activate(UUID templateId, String actor) {
        PromptTemplate current = template(templateId);
        requireStatus(current, PromptStatus.APPROVED, PromptStatus.DEPRECATED);
        if (sameActor(current.approvedBy(), actor)) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, "Prompt 审批人不能激活同一版本");
        }
        repository.deactivateActive(current.promptKey(), current.version());
        PromptTemplate active =
                withStatus(
                        current,
                        PromptStatus.ACTIVE,
                        actor,
                        actor,
                        clock.instant(),
                        clock.instant(),
                        clock.instant());
        repository.update(active);
        repository.appendAudit(active.promptKey(), active.version(), "ACTIVATED", actor, Map.of("status", "ACTIVE"));
        return active;
    }

    @Transactional(readOnly = true)
    public List<PromptChangeRequest> changeRequests(UUID templateId) {
        return repository.changeRequests(templateId);
    }

    @Transactional(readOnly = true)
    public PromptVersionReview review(UUID templateId) {
        PromptTemplate candidate = template(templateId);
        PromptTemplate active = repository.active(candidate.promptKey()).orElse(null);
        return new PromptVersionReview(
                candidate,
                active,
                compare(active, candidate),
                repository.changeRequests(templateId),
                repository.auditLog(candidate.promptKey(), candidate.version(), 50));
    }

    private PromptTemplate template(UUID id) {
        return repository.findById(id)
                .orElseThrow(
                        () ->
                                new CampusFundFlowException(
                                        CampusFundFlowErrorCode.VALIDATION_FAILED, "Prompt 不存在"));
    }

    private PromptChangeRequest changeRequest(UUID id) {
        return repository.findChangeRequest(id)
                .orElseThrow(
                        () ->
                                new CampusFundFlowException(
                                        CampusFundFlowErrorCode.VALIDATION_FAILED, "Prompt 审批单不存在"));
    }

    private static PromptTemplate withStatus(
            PromptTemplate current,
            PromptStatus status,
            String updatedBy,
            String approvedBy,
            Instant approvedAt,
            Instant activatedAt,
            Instant updatedAt) {
        return new PromptTemplate(
                current.id(),
                current.promptKey(),
                current.version(),
                current.name(),
                current.description(),
                current.content(),
                current.variableSchema(),
                current.modelName(),
                current.temperature(),
                current.maxTokens(),
                status,
                current.promptHash(),
                current.createdBy(),
                updatedBy,
                approvedBy == null ? current.approvedBy() : approvedBy,
                current.createdAt(),
                updatedAt,
                approvedAt == null ? current.approvedAt() : approvedAt,
                activatedAt == null ? current.activatedAt() : activatedAt,
                current.replacedVersion());
    }

    private static void requireStatus(PromptTemplate template, PromptStatus... allowed) {
        for (PromptStatus status : allowed) {
            if (template.status() == status) {
                return;
            }
        }
        throw new CampusFundFlowException(
                CampusFundFlowErrorCode.INVALID_STATE_TRANSITION,
                "Prompt 当前状态不允许执行该操作：" + template.status());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, field + "不能为空");
        }
        return value.trim();
    }

    private static void requireComment(String comment, String action) {
        if (comment == null || comment.isBlank()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.VALIDATION_FAILED, action + " Prompt 时必须填写意见");
        }
    }

    private static boolean sameActor(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private static PromptDiff compare(PromptTemplate active, PromptTemplate candidate) {
        if (active == null) {
            return new PromptDiff(
                    0,
                    lineCount(candidate.content()),
                    0,
                    List.of("当前 Prompt Key 尚无 Active 版本"),
                    true,
                    true,
                    true);
        }
        List<String> changedFields = new java.util.ArrayList<>();
        if (!active.modelName().equals(candidate.modelName())) {
            changedFields.add("modelName");
        }
        if (active.temperature().compareTo(candidate.temperature()) != 0) {
            changedFields.add("temperature");
        }
        if (active.maxTokens() != candidate.maxTokens()) {
            changedFields.add("maxTokens");
        }
        if (!active.variableSchema().equals(candidate.variableSchema())) {
            changedFields.add("variableSchema");
        }
        if (!active.content().equals(candidate.content())) {
            changedFields.add("content");
        }
        int activeLines = lineCount(active.content());
        int candidateLines = lineCount(candidate.content());
        int commonPrefix = commonPrefixLines(active.content(), candidate.content());
        return new PromptDiff(
                activeLines,
                candidateLines,
                Math.abs(candidateLines - activeLines),
                List.copyOf(changedFields),
                !active.promptHash().equals(candidate.promptHash()),
                candidate.status() == PromptStatus.DEPRECATED,
                active.id().equals(candidate.id()));
    }

    private static int lineCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return value.split("\\R", -1).length;
    }

    private static int commonPrefixLines(String left, String right) {
        String[] leftLines = left == null ? new String[0] : left.split("\\R", -1);
        String[] rightLines = right == null ? new String[0] : right.split("\\R", -1);
        int count = 0;
        while (count < leftLines.length
                && count < rightLines.length
                && leftLines[count].equals(rightLines[count])) {
            count++;
        }
        return count;
    }

    public record PromptVersionReview(
            PromptTemplate candidate,
            PromptTemplate active,
            PromptDiff diff,
            List<PromptChangeRequest> changes,
            List<PromptTemplateRepository.PromptAuditEvent> auditEvents) {}

    public record PromptDiff(
            int activeLineCount,
            int candidateLineCount,
            int lineDelta,
            List<String> changedFields,
            boolean contentChanged,
            boolean rollbackCandidate,
            boolean currentlyActive) {}
}
