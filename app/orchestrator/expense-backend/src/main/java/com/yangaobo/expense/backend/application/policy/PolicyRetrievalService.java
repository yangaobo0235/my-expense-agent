package com.yangaobo.expense.backend.application.policy;

import com.yangaobo.expense.backend.domain.model.ExpensePolicy;
import com.yangaobo.expense.backend.domain.model.PolicyChunk;
import com.yangaobo.expense.backend.domain.repository.ExpensePolicyRepository;
import com.yangaobo.expense.backend.domain.repository.PolicyCatalogEntry;
import com.yangaobo.expense.backend.domain.repository.PolicySearchMatch;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard.GuardMode;
import com.yangaobo.expense.backend.application.governance.SensitiveDataMasker;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyRetrievalService {

    private final ExpensePolicyRepository repository;
    private final PolicyDocumentChunker chunker;
    private final PolicyEmbeddingModel embeddingModel;
    private final AgentInputGuard inputGuard;
    private final SensitiveDataMasker masker;
    private final Clock clock;

    public PolicyRetrievalService(
            ExpensePolicyRepository repository,
            PolicyDocumentChunker chunker,
            PolicyEmbeddingModel embeddingModel,
            AgentInputGuard inputGuard,
            SensitiveDataMasker masker,
            Clock clock) {
        this.repository = repository;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.inputGuard = inputGuard;
        this.masker = masker;
        this.clock = clock;
    }

    @Transactional
    public PolicyImportResult importPolicy(ImportPolicyCommand command) {
        String sanitizedMarkdown =
                inputGuard
                        .inspect("policy-import.markdown", command.markdownContent(), GuardMode.REPORT_ONLY)
                        .sanitizedInput();
        sanitizedMarkdown = masker.mask(sanitizedMarkdown);
        List<PolicyChunkDraft> drafts = chunker.chunk(sanitizedMarkdown);
        Instant now = clock.instant();
        UUID policyId = UUID.randomUUID();
        String contentHash = sha256(sanitizedMarkdown);
        ExpensePolicy policy =
                new ExpensePolicy(
                        policyId,
                        command.policyCode(),
                        masker.mask(command.name()),
                        command.category(),
                        command.region(),
                        command.employeeGrade(),
                        command.version(),
                        command.effectiveFrom(),
                        command.effectiveTo(),
                        command.status(),
                        command.sourceUri(),
                        contentHash,
                        now,
                        now);
        List<PolicyChunk> chunks =
                java.util.stream.IntStream.range(0, drafts.size())
                        .mapToObj(
                                index -> {
                                    PolicyChunkDraft draft = drafts.get(index);
                                    Map<String, String> metadata =
                                            Map.of(
                                                    "policy_id", policyId.toString(),
                                                    "policy_version", policy.version(),
                                                    "expense_type", policy.category(),
                                                    "region", policy.region(),
                                                    "employee_level", policy.employeeGrade(),
                                                    "section", draft.section());
                                    return new PolicyChunk(
                                            UUID.randomUUID(),
                                            policyId,
                                            index,
                                            draft.section(),
                                            draft.content(),
                                            draft.tokenCount(),
                                            metadata,
                                            embeddingModel.embed(
                                                    draft.section() + "\n" + draft.content()),
                                            now);
                                })
                        .toList();
        repository.insert(policy, chunks);
        return new PolicyImportResult(
                policyId,
                policy.policyCode(),
                policy.version(),
                chunks.size(),
                embeddingModel.modelName(),
                contentHash);
    }

    public List<PolicySearchMatch> search(PolicySearchQuery query) {
        String text =
                inputGuard
                        .inspect("policy-search.query", required(query.query(), "query"), GuardMode.REPORT_ONLY)
                        .sanitizedInput();
        String category = required(query.category(), "category");
        String region = required(query.region(), "region");
        String employeeGrade = required(query.employeeGrade(), "employeeGrade");
        LocalDate expenseDate =
                query.expenseDate() == null ? LocalDate.now(clock) : query.expenseDate();
        int limit = query.limit() <= 0 ? 5 : Math.min(query.limit(), 20);
        double minimumScore =
                query.minimumScore() <= 0 ? 0.55 : Math.min(query.minimumScore(), 1.0);
        return repository.search(
                embeddingModel.embed(text),
                category,
                region,
                employeeGrade,
                expenseDate,
                limit,
                minimumScore);
    }

    public List<PolicyCatalogEntry> listCatalog() {
        return repository.listCatalog();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.VALIDATION_FAILED, field + "不能为空");
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
        }
    }
}
