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

@SpringBootTest
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
        String uniqueCode = "IT-HOTEL-" + UUID.randomUUID();
        var imported =
                policyService.importPolicy(
                        new ImportPolicyCommand(
                                uniqueCode,
                                "集成测试住宿费制度",
                                "住宿费",
                                "CN",
                                "ALL",
                                "1.0",
                                LocalDate.of(2026, 1, 1),
                                null,
                                PolicyStatus.ACTIVE,
                                "policy://expense-flow/HOTEL-CN-V1",
                                """
                                # 住宿费制度

                                ## 金额上限

                                一线城市普通员工每人每晚住宿上限为六百元。

                                ## 必需凭证

                                必须提交酒店发票和住宿明细。
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
                                "一线城市普通员工每人每晚住宿上限为六百元",
                                "住宿费",
                                "CN",
                                "G6",
                                LocalDate.of(2026, 6, 18),
                                5,
                                0.10));

        assertThat(matches).isNotEmpty();
        assertThat(matches.getFirst().policyId()).isEqualTo(policyId);
        assertThat(matches.getFirst().section()).isEqualTo("金额上限");

        var filteredOut =
                policyService.search(
                        new PolicySearchQuery(
                                "住宿上限",
                                "餐饮费",
                                "CN",
                                "G6",
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
