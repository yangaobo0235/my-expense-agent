package com.yangaobo.expense.backend.infrastructure.persistence;

import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.backend.domain.repository.ExpenseCaseRepository;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExpenseCaseRepository implements ExpenseCaseRepository {

    private static final RowMapper<ExpenseCase> ROW_MAPPER =
            JdbcExpenseCaseRepository::mapExpenseCase;

    private final JdbcClient jdbcClient;

    public JdbcExpenseCaseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ExpenseCase insert(ExpenseCase expenseCase) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_case (
                            id, case_number, owner_subject, applicant_name, project_code,
                            title, currency, claimed_amount, status, risk_level, risk_score,
                            failure_stage, failure_reason, version, created_at, updated_at
                        ) VALUES (
                            :id, :caseNumber, :ownerSubject, :applicantName, :projectCode,
                            :title, :currency, :claimedAmount, :status, :riskLevel, :riskScore,
                            :failureStage, :failureReason, :version, :createdAt, :updatedAt
                        )
                        """)
                .param("id", expenseCase.id())
                .param("caseNumber", expenseCase.caseNumber())
                .param("ownerSubject", expenseCase.ownerSubject())
                .param("applicantName", expenseCase.applicantName())
                .param("projectCode", expenseCase.projectCode())
                .param("title", expenseCase.title())
                .param("currency", expenseCase.claimedAmount().currency())
                .param("claimedAmount", expenseCase.claimedAmount().amount())
                .param("status", expenseCase.status().name())
                .param("riskLevel", nameOf(expenseCase.riskLevel()))
                .param("riskScore", expenseCase.riskScore())
                .param("failureStage", expenseCase.failureStage())
                .param("failureReason", expenseCase.failureReason())
                .param("version", expenseCase.version())
                .param("createdAt", Timestamp.from(expenseCase.createdAt()))
                .param("updatedAt", Timestamp.from(expenseCase.updatedAt()))
                .update();
        return expenseCase;
    }

    @Override
    public Optional<ExpenseCase> findById(UUID id) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_number, owner_subject, applicant_name, project_code,
                               title, currency, claimed_amount, status, risk_level, risk_score,
                               failure_stage, failure_reason, version, created_at, updated_at
                        FROM expense_case
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public ExpenseCasePage search(ExpenseCaseSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> parameters = new HashMap<>();
        appendFilter(where, parameters, "owner_subject = :ownerSubject", "ownerSubject", criteria.ownerSubject());
        appendFilter(where, parameters, "status = :status", "status", nameOf(criteria.status()));
        appendFilter(where, parameters, "risk_level = :riskLevel", "riskLevel", criteria.riskLevel());
        if (criteria.applicant() != null) {
            where.append(" AND applicant_name ILIKE :applicant");
            parameters.put("applicant", "%" + criteria.applicant() + "%");
        }
        if (criteria.createdFrom() != null) {
            where.append(" AND created_at >= :createdFrom");
            parameters.put("createdFrom", Timestamp.from(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            where.append(" AND created_at < :createdTo");
            parameters.put("createdTo", Timestamp.from(criteria.createdTo()));
        }

        var countQuery =
                jdbcClient.sql("SELECT COUNT(*) FROM expense_case" + where);
        var dataQuery =
                jdbcClient.sql(
                        """
                        SELECT id, case_number, owner_subject, applicant_name, project_code,
                               title, currency, claimed_amount, status, risk_level, risk_score,
                               failure_stage, failure_reason, version, created_at, updated_at
                        FROM expense_case
                        """
                                + where
                                + " ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset");
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            countQuery = countQuery.param(parameter.getKey(), parameter.getValue());
            dataQuery = dataQuery.param(parameter.getKey(), parameter.getValue());
        }
        long total = countQuery.query(Long.class).single();
        List<ExpenseCase> items =
                dataQuery
                        .param("limit", criteria.size())
                        .param("offset", (long) criteria.page() * criteria.size())
                        .query(ROW_MAPPER)
                        .list();
        return new ExpenseCasePage(items, total);
    }

    @Override
    public ExpenseCase update(ExpenseCase expenseCase, long expectedVersion) {
        int updated =
                jdbcClient
                        .sql(
                                """
                                UPDATE expense_case
                                SET applicant_name = :applicantName,
                                    project_code = :projectCode,
                                    title = :title,
                                    currency = :currency,
                                    claimed_amount = :claimedAmount,
                                    status = :status,
                                    risk_level = :riskLevel,
                                    risk_score = :riskScore,
                                    failure_stage = :failureStage,
                                    failure_reason = :failureReason,
                                    version = :newVersion,
                                    updated_at = :updatedAt
                                WHERE id = :id AND version = :expectedVersion
                                """)
                        .param("applicantName", expenseCase.applicantName())
                        .param("projectCode", expenseCase.projectCode())
                        .param("title", expenseCase.title())
                        .param("currency", expenseCase.claimedAmount().currency())
                        .param("claimedAmount", expenseCase.claimedAmount().amount())
                        .param("status", expenseCase.status().name())
                        .param("riskLevel", nameOf(expenseCase.riskLevel()))
                        .param("riskScore", expenseCase.riskScore())
                        .param("failureStage", expenseCase.failureStage())
                        .param("failureReason", expenseCase.failureReason())
                        .param("newVersion", expenseCase.version())
                        .param("updatedAt", Timestamp.from(expenseCase.updatedAt()))
                        .param("id", expenseCase.id())
                        .param("expectedVersion", expectedVersion)
                        .update();
        if (updated != 1) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "Expense case changed while it was being processed");
        }
        return expenseCase;
    }

    @Override
    public void deleteById(UUID id, long expectedVersion) {
        int deleted =
                jdbcClient
                        .sql(
                                """
                                DELETE FROM expense_case
                                WHERE id = :id AND version = :expectedVersion
                                """)
                        .param("id", id)
                        .param("expectedVersion", expectedVersion)
                        .update();
        if (deleted != 1) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "Expense case changed while it was being deleted");
        }
    }

    private static ExpenseCase mapExpenseCase(ResultSet resultSet, int rowNumber)
            throws SQLException {
        Integer riskScore = (Integer) resultSet.getObject("risk_score");
        String riskLevel = resultSet.getString("risk_level");
        return new ExpenseCase(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("case_number"),
                resultSet.getString("owner_subject"),
                resultSet.getString("applicant_name"),
                resultSet.getString("project_code"),
                resultSet.getString("title"),
                new Money(
                        resultSet.getBigDecimal("claimed_amount"),
                        resultSet.getString("currency")),
                ExpenseCaseStatus.valueOf(resultSet.getString("status")),
                riskLevel == null ? null : RiskLevel.valueOf(riskLevel),
                riskScore,
                resultSet.getString("failure_stage"),
                resultSet.getString("failure_reason"),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static void appendFilter(
            StringBuilder where,
            Map<String, Object> parameters,
            String expression,
            String name,
            Object value) {
        if (value != null) {
            where.append(" AND ").append(expression);
            parameters.put(name, value);
        }
    }
}
