package com.yangaobo.expense.backend.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.application.policy.ImportPolicyCommand;
import com.yangaobo.expense.backend.application.policy.PolicyRetrievalService;
import com.yangaobo.expense.backend.application.policy.PolicySearchQuery;
import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=2")
@EnabledIfEnvironmentVariable(named = "EXPENSE_IT_DATABASE_URL", matches = ".+")
class PolicyRagExternalIntegrationTest {

    @Autowired private PolicyRetrievalService policyService;
    @Autowired private JdbcClient jdbcClient;

    private UUID policyId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("EXPENSE_IT_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> required("EXPENSE_IT_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> required("EXPENSE_IT_DATABASE_PASSWORD"));
        registry.add("expense.ai.embedding.provider", () -> "deterministic");
    }

    @AfterEach
    void removePolicy() {
        if (policyId != null) {
            jdbcClient
                    .sql("DELETE FROM expense_policy WHERE id = :id")
                    .param("id", policyId)
                    .update();
        }
    }

    @Test
    void shouldFilterMetadataAndReturnTraceableCitation() {
        String uniqueCode = "IT-COMPETITION-TRAVEL-" + UUID.randomUUID();
        var imported =
                policyService.importPolicy(
                        new ImportPolicyCommand(
                                uniqueCode,
                                "集成测试学生竞赛差旅经费制度",
                                "竞赛差旅费",
                                "CN",
                                "ALL",
                                "1.0",
                                LocalDate.of(2026, 1, 1),
                                null,
                                PolicyStatus.ACTIVE,
                                "policy://my-expense-agent/COMPETITION-TRAVEL-V1",
                                """
                                # 学生竞赛差旅经费制度

                                ## 竞赛住宿标准

                                学生参加校级认定竞赛的住宿费上限为每人每晚三百五十元。

                                ## 必需凭证

                                必须提交竞赛通知、指导老师确认意见、酒店发票和住宿明细。
                                """));
        policyId = imported.policyId();

        var catalogEntry =
                policyService.listCatalog().stream()
                        .filter(entry -> entry.id().equals(policyId))
                        .findFirst()
                        .orElseThrow();
        assertThat(catalogEntry.chunkCount()).isEqualTo(imported.chunkCount());
        assertThat(catalogEntry.indexedChunkCount()).isEqualTo(imported.chunkCount());
        assertThat(catalogEntry.status()).isEqualTo(PolicyStatus.ACTIVE);

        var matches =
                policyService.search(
                        new PolicySearchQuery(
                                "学生参加校级认定竞赛的住宿费上限为每人每晚三百五十元",
                                "竞赛差旅费",
                                "CN",
                                "STUDENT",
                                LocalDate.of(2026, 6, 18),
                                5,
                                0.10));

        assertThat(matches).isNotEmpty();
        assertThat(matches.getFirst().policyId()).isEqualTo(policyId);
        assertThat(matches.getFirst().section()).isEqualTo("竞赛住宿标准");

        var filteredOut =
                policyService.search(
                        new PolicySearchQuery(
                                "竞赛住宿上限",
                                "实验耗材费",
                                "CN",
                                "STUDENT",
                                LocalDate.of(2026, 6, 18),
                                5,
                                0.10));
        assertThat(filteredOut).isEmpty();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
