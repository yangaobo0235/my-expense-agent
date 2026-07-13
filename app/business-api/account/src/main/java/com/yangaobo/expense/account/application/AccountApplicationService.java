package com.yangaobo.expense.account.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountApplicationService {

    private final JdbcClient jdbcClient;

    public AccountApplicationService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ApplicantProfile getApplicantProfile(String applicantId) {
        return getApplicantProfile(applicantId, null);
    }

    public ApplicantProfile getApplicantProfile(String applicantId, String projectCode) {
        String id = required(applicantId, "applicantId");
        String project = optional(projectCode);
        JdbcClient.StatementSpec query =
                project == null
                        ? jdbcClient
                                .sql(
                                        """
                                        SELECT applicant_id, name, project_code, campus_level, region,
                                               budget_balance, currency
                                        FROM campus_fund_applicant_account
                                        WHERE applicant_id = :applicantId
                                        ORDER BY project_code
                                        LIMIT 1
                                        """)
                                .param("applicantId", id)
                        : jdbcClient
                                .sql(
                                        """
                                        SELECT applicant_id, name, project_code, campus_level, region,
                                               budget_balance, currency
                                        FROM campus_fund_applicant_account
                                        WHERE applicant_id = :applicantId
                                          AND project_code = :projectCode
                                        ORDER BY project_code
                                        LIMIT 1
                                        """)
                                .param("applicantId", id)
                                .param("projectCode", project);
        return query.query(
                        (rs, rowNum) ->
                                new ApplicantProfile(
                                        rs.getString("applicant_id"),
                                        rs.getString("name"),
                                        rs.getString("project_code"),
                                        rs.getString("campus_level"),
                                        rs.getString("region"),
                                        reimbursementAccounts(id),
                                        rs.getBigDecimal("budget_balance"),
                                        rs.getString("currency")))
                .optional()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        project == null
                                                ? "申请人不存在或未加入有效项目：" + id
                                                : "申请人未加入项目：" + id + " / " + project));
    }

    public List<String> getReimbursementAccounts(String applicantId) {
        getApplicantProfile(applicantId);
        return reimbursementAccounts(applicantId);
    }

    public ProjectBudgetBalance getProjectBudgetBalance(String applicantId) {
        return getProjectBudgetBalance(applicantId, null);
    }

    public ProjectBudgetBalance getProjectBudgetBalance(String applicantId, String projectCode) {
        ApplicantProfile profile = getApplicantProfile(applicantId, projectCode);
        return jdbcClient
                .sql(
                        """
                        SELECT budget.project_code, budget.total_amount, budget.available_amount,
                               budget.currency, budget.version, budget.updated_at
                        FROM campus_fund_project_budget budget
                        JOIN campus_fund_project_member member
                          ON member.project_code = budget.project_code
                        WHERE member.applicant_id = :applicantId
                          AND budget.project_code = :projectCode
                        """)
                .param("applicantId", profile.applicantId())
                .param("projectCode", profile.projectCode())
                .query(
                        (rs, rowNum) ->
                                new ProjectBudgetBalance(
                                        profile.applicantId(),
                                        rs.getString("project_code"),
                                        rs.getBigDecimal("total_amount"),
                                        rs.getBigDecimal("available_amount"),
                                        rs.getString("currency"),
                                        rs.getLong("version"),
                                        rs.getTimestamp("updated_at").toInstant()))
                .single();
    }

    @Transactional
    public BudgetDebit debitProjectBudget(
            String requestId,
            UUID caseId,
            String projectCode,
            String applicantId,
            BigDecimal amount,
            String currency) {
        String normalizedRequestId = required(requestId, "requestId");
        String normalizedProjectCode = required(projectCode, "projectCode");
        String normalizedApplicantId = required(applicantId, "applicantId");
        String normalizedCurrency = required(currency, "currency").toUpperCase();
        if (caseId == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("caseId 和扣减金额必须有效，金额必须大于 0");
        }

        Optional<BudgetDebit> existing = findDebit(normalizedRequestId);
        if (existing.isPresent()) {
            assertSameDebit(
                    existing.get(),
                    caseId,
                    normalizedProjectCode,
                    normalizedApplicantId,
                    amount,
                    normalizedCurrency);
            return existing.get();
        }

        Boolean member =
                jdbcClient
                        .sql(
                                """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM campus_fund_project_member
                                    WHERE project_code = :projectCode
                                      AND applicant_id = :applicantId
                                )
                                """)
                        .param("projectCode", normalizedProjectCode)
                        .param("applicantId", normalizedApplicantId)
                        .query(Boolean.class)
                        .single();
        if (!Boolean.TRUE.equals(member)) {
            throw new IllegalArgumentException("申请人不属于指定经费项目");
        }

        LockedBudget budget =
                jdbcClient
                        .sql(
                                """
                                SELECT available_amount, currency
                                FROM campus_fund_project_budget
                                WHERE project_code = :projectCode
                                FOR UPDATE
                                """)
                        .param("projectCode", normalizedProjectCode)
                        .query(
                                (rs, rowNum) ->
                                        new LockedBudget(
                                                rs.getBigDecimal("available_amount"),
                                                rs.getString("currency")))
                        .optional()
                        .orElseThrow(() -> new IllegalArgumentException("经费项目预算不存在"));
        if (!budget.currency().equalsIgnoreCase(normalizedCurrency)) {
            throw new IllegalArgumentException("报销币种与项目预算币种不一致");
        }
        if (budget.available().compareTo(amount) < 0) {
            throw new IllegalArgumentException("项目可用经费不足");
        }

        BigDecimal remaining = budget.available().subtract(amount);
        Instant now = Instant.now();
        BudgetDebit created =
                new BudgetDebit(
                        UUID.randomUUID(),
                        normalizedRequestId,
                        caseId,
                        normalizedProjectCode,
                        normalizedApplicantId,
                        amount,
                        normalizedCurrency,
                        "DEBITED",
                        remaining,
                        now);
        jdbcClient
                .sql(
                        """
                        UPDATE campus_fund_project_budget
                        SET available_amount = :remaining,
                            version = version + 1,
                            updated_at = :updatedAt
                        WHERE project_code = :projectCode
                        """)
                .param("remaining", remaining)
                .param("updatedAt", Timestamp.from(now))
                .param("projectCode", normalizedProjectCode)
                .update();
        jdbcClient
                .sql(
                        """
                        INSERT INTO campus_fund_budget_debit (
                            debit_id, request_id, case_id, project_code, applicant_id,
                            amount, currency, status, remaining_available, created_at
                        ) VALUES (
                            :debitId, :requestId, :caseId, :projectCode, :applicantId,
                            :amount, :currency, :status, :remainingAvailable, :createdAt
                        )
                        """)
                .param("debitId", created.debitId())
                .param("requestId", created.requestId())
                .param("caseId", created.caseId())
                .param("projectCode", created.projectCode())
                .param("applicantId", created.applicantId())
                .param("amount", created.amount())
                .param("currency", created.currency())
                .param("status", created.status())
                .param("remainingAvailable", created.remainingAvailable())
                .param("createdAt", Timestamp.from(created.createdAt()))
                .update();
        return created;
    }

    private Optional<BudgetDebit> findDebit(String requestId) {
        return jdbcClient
                .sql(
                        """
                        SELECT debit_id, request_id, case_id, project_code, applicant_id,
                               amount, currency, status, remaining_available, created_at
                        FROM campus_fund_budget_debit
                        WHERE request_id = :requestId
                        """)
                .param("requestId", requestId)
                .query(
                        (rs, rowNum) ->
                                new BudgetDebit(
                                        rs.getObject("debit_id", UUID.class),
                                        rs.getString("request_id"),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("project_code"),
                                        rs.getString("applicant_id"),
                                        rs.getBigDecimal("amount"),
                                        rs.getString("currency"),
                                        rs.getString("status"),
                                        rs.getBigDecimal("remaining_available"),
                                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    private static void assertSameDebit(
            BudgetDebit existing,
            UUID caseId,
            String projectCode,
            String applicantId,
            BigDecimal amount,
            String currency) {
        if (!existing.caseId().equals(caseId)
                || !existing.projectCode().equals(projectCode)
                || !existing.applicantId().equals(applicantId)
                || existing.amount().compareTo(amount) != 0
                || !existing.currency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("requestId 已被其他预算扣减请求使用");
        }
    }

    private List<String> reimbursementAccounts(String applicantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT account_type
                        FROM campus_fund_reimbursement_account
                        WHERE applicant_id = :applicantId
                        ORDER BY account_type
                        """)
                .param("applicantId", applicantId)
                .query(String.class)
                .list();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ApplicantProfile(
            String applicantId,
            String name,
            String projectCode,
            String campusLevel,
            String region,
            List<String> reimbursementAccounts,
            BigDecimal budgetBalance,
            String currency) {

        public ApplicantProfile {
            reimbursementAccounts = List.copyOf(reimbursementAccounts);
        }
    }

    public record ProjectBudgetBalance(
            String applicantId,
            String projectCode,
            BigDecimal total,
            BigDecimal available,
            String currency,
            long version,
            Instant updatedAt) {}

    public record BudgetDebit(
            UUID debitId,
            String requestId,
            UUID caseId,
            String projectCode,
            String applicantId,
            BigDecimal amount,
            String currency,
            String status,
            BigDecimal remainingAvailable,
            Instant createdAt) {}

    private record LockedBudget(BigDecimal available, String currency) {}
}
