package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.report.ReviewReport;
import com.yangaobo.expense.backend.application.report.ReviewReportRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReviewReportRepository implements ReviewReportRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcReviewReportRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewReport save(ReviewReport report) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_review_report (
                            id, case_id, summary, risk_explanation, policy_citations,
                            human_review_hints, limitations, model_name, prompt_version,
                            created_at
                        ) VALUES (
                            :id, :caseId, :summary, CAST(:riskExplanation AS jsonb),
                            CAST(:policyCitations AS jsonb), CAST(:humanReviewHints AS jsonb),
                            CAST(:limitations AS jsonb), :modelName, :promptVersion,
                            :createdAt
                        )
                        """)
                .param("id", report.id())
                .param("caseId", report.caseId())
                .param("summary", report.summary())
                .param("riskExplanation", write(report.riskExplanation()))
                .param("policyCitations", write(report.policyCitations()))
                .param("humanReviewHints", write(report.humanReviewHints()))
                .param("limitations", write(report.limitations()))
                .param("modelName", report.modelName())
                .param("promptVersion", report.promptVersion())
                .param("createdAt", Timestamp.from(report.createdAt()))
                .update();
        return report;
    }

    @Override
    public Optional<ReviewReport> latest(UUID caseId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, summary, risk_explanation::text,
                               policy_citations::text, human_review_hints::text,
                               limitations::text, model_name, prompt_version, created_at
                        FROM expense_review_report
                        WHERE case_id = :caseId
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """)
                .param("caseId", caseId)
                .query(
                        (rs, row) ->
                                new ReviewReport(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("summary"),
                                        readList(rs.getString("risk_explanation")),
                                        readCitations(rs.getString("policy_citations")),
                                        readList(rs.getString("human_review_hints")),
                                        readList(rs.getString("limitations")),
                                        rs.getString("model_name"),
                                        rs.getString("prompt_version"),
                                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审核报告 JSON 字段格式无效", exception);
        }
    }

    private List<ReviewReport.PolicyCitation> readCitations(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审核报告引用字段格式无效", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审核报告序列化失败", exception);
        }
    }
}
