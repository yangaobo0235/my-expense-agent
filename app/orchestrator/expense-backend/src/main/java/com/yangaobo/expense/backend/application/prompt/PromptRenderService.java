package com.yangaobo.expense.backend.application.prompt;

import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard.GuardMode;
import com.yangaobo.expense.backend.application.governance.SensitiveDataMasker;
import java.time.Clock;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromptRenderService {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    private final PromptTemplateRepository repository;
    private final Clock clock;
    private final AgentInputGuard inputGuard;
    private final SensitiveDataMasker masker;

    public PromptRenderService(
            PromptTemplateRepository repository,
            Clock clock,
            AgentInputGuard inputGuard,
            SensitiveDataMasker masker) {
        this.repository = repository;
        this.clock = clock;
        this.inputGuard = inputGuard;
        this.masker = masker;
    }

    @Transactional
    public RenderedPrompt render(String promptKey, Map<String, ?> variables) {
        PromptTemplate template = activeOrSeed(promptKey);
        Map<String, Object> rawVariables = new java.util.LinkedHashMap<>();
        if (variables != null) {
            variables.forEach((key, value) -> rawVariables.put(key, value));
        }
        Map<String, ?> sanitizedVariables = masker.maskMap(rawVariables);
        String content = renderContent(template.content(), sanitizedVariables);
        inputGuard.inspect("prompt:" + promptKey, content, GuardMode.REPORT_ONLY);
        return new RenderedPrompt(
                template.promptKey(),
                template.version(),
                content,
                ModelHash.sha256(content),
                template.modelName(),
                template.temperature(),
                template.maxTokens());
    }

    @Transactional
    public PromptTemplate activeOrSeed(String promptKey) {
        return repository.active(promptKey)
                .orElseGet(
                        () -> {
                            PromptTemplate seed =
                                    PromptDefaults.template(promptKey, "SYSTEM_SEED", clock.instant());
                            repository.save(seed);
                            repository.appendAudit(
                                    seed.promptKey(),
                                    seed.version(),
                                    "SEEDED_ACTIVE",
                                    "SYSTEM_SEED",
                                    Map.of("status", seed.status().name()));
                            return seed;
                        });
    }

    private static String renderContent(String template, Map<String, ?> variables) {
        java.util.regex.Matcher matcher = VARIABLE.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            if (value == null) {
                throw new ExpenseFlowException(
                        ExpenseFlowErrorCode.VALIDATION_FAILED,
                        "Prompt 变量缺失：" + key);
            }
            matcher.appendReplacement(rendered, java.util.regex.Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
