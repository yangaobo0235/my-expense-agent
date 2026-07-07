package com.yangaobo.expense.backend.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.backend.application.CreateExpenseCaseCommand;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.repository.ExpenseCaseRepository;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ExpenseCaseExternalPostgresIntegrationTest {

    @Autowired private ExpenseCaseApplicationService applicationService;
    @Autowired private ExpenseCaseRepository repository;
    @Autowired private JdbcClient jdbcClient;

    private UUID createdCaseId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("EXPENSE_IT_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> required("EXPENSE_IT_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> required("EXPENSE_IT_DATABASE_PASSWORD"));
    }

    @AfterEach
    void removeTestCase() {
        if (createdCaseId != null) {
            jdbcClient
                    .sql("DELETE FROM expense_case WHERE id = :id")
                    .param("id", createdCaseId)
                    .update();
        }
    }

    @Test
    void repositoryWorksAgainstConfiguredPostgres() {
        ExpenseCase created =
                applicationService.create(
                        new CreateExpenseCaseCommand(
                                "integration-user-a",
                                "Integration User",
                                "TEST",
                                "External PostgreSQL verification",
                                new BigDecimal("88.00"),
                                "CNY"));
        createdCaseId = created.id();

        assertThat(applicationService.getOwned(created.id(), "integration-user-a"))
                .isEqualTo(created);
        var ownedPage =
                applicationService.search(
                        new ExpenseCaseApplicationService.ExpenseCaseQuery(
                                "integration-user-a",
                                false,
                                null,
                                null,
                                "Integration",
                                Instant.now().minus(1, ChronoUnit.DAYS),
                                Instant.now().plus(1, ChronoUnit.DAYS),
                                0,
                                20));
        assertThat(ownedPage.items()).extracting(ExpenseCase::id).contains(created.id());
        assertThat(ownedPage.total()).isPositive();

        var otherUserPage =
                applicationService.search(
                        new ExpenseCaseApplicationService.ExpenseCaseQuery(
                                "integration-user-b",
                                false,
                                null,
                                null,
                                "Integration",
                                null,
                                null,
                                0,
                                20));
        assertThat(otherUserPage.items()).extracting(ExpenseCase::id).doesNotContain(created.id());

        var reviewerPage =
                applicationService.search(
                        new ExpenseCaseApplicationService.ExpenseCaseQuery(
                                "reviewer",
                                true,
                                null,
                                null,
                                "Integration",
                                null,
                                null,
                                0,
                                20));
        assertThat(reviewerPage.items()).extracting(ExpenseCase::id).contains(created.id());
        assertThatThrownBy(
                        () -> applicationService.getOwned(created.id(), "integration-user-b"))
                .isInstanceOf(ExpenseFlowException.class)
                .satisfies(
                        error ->
                                assertThat(((ExpenseFlowException) error).code())
                                        .isEqualTo(ExpenseFlowErrorCode.ACCESS_DENIED));

        ExpenseCase uploaded =
                applicationService.transition(created.id(), ExpenseCaseStatus.UPLOADED);
        assertThat(uploaded.version()).isEqualTo(1);

        ExpenseCase stale =
                created.transitionTo(ExpenseCaseStatus.UPLOADED, Instant.now());
        assertThatThrownBy(() -> repository.update(stale, 0))
                .isInstanceOf(ExpenseFlowException.class)
                .satisfies(
                        error ->
                                assertThat(((ExpenseFlowException) error).code())
                                        .isEqualTo(
                                                ExpenseFlowErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
