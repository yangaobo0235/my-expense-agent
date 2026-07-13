package com.yangaobo.expense.backend.application.governance;

import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentInputGuard {

    private final SensitiveDataMasker masker;

    public AgentInputGuard(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    public GuardResult inspect(String surface, String input, GuardMode mode) {
        String masked = masker.mask(input);
        List<String> violations = violations(masked);
        GuardResult result =
                new GuardResult(
                        surface,
                        masked,
                        violations.isEmpty() ? "LOW" : violations.size() > 1 ? "HIGH" : "MEDIUM",
                        violations);
        if (mode == GuardMode.BLOCK && !violations.isEmpty()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.VALIDATION_FAILED,
                    "Agent 输入安全检查未通过：" + String.join("、", violations));
        }
        return result;
    }

    public Map<String, Object> inspectMap(String surface, Map<String, Object> input, GuardMode mode) {
        Map<String, Object> masked = masker.maskMap(input);
        inspect(surface, String.valueOf(masked), mode);
        return masked;
    }

    private static List<String> violations(String input) {
        String normalized = input == null ? "" : input.toLowerCase(java.util.Locale.ROOT);
        return List.of(
                        Map.entry("PROMPT_INJECTION", List.of("忽略之前", "ignore previous", "越过规则", "绕过审批")),
                        Map.entry("UNAUTHORIZED_APPROVAL", List.of("直接批准", "approve all", "跳过人工", "skip human")),
                        Map.entry("UNAUTHORIZED_POSTING", List.of("submit_fund_posting", "直接入账", "发起入账")),
                        Map.entry("SECRET_EXFILTRATION", List.of("泄露 token", "leak token", "泄露密钥", "leak secret")))
                .stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalized::contains))
                .map(Map.Entry::getKey)
                .distinct()
                .toList();
    }

    public enum GuardMode {
        REPORT_ONLY,
        BLOCK
    }

    public record GuardResult(
            String surface,
            String sanitizedInput,
            String riskLevel,
            List<String> violations) {

        public GuardResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }
}
