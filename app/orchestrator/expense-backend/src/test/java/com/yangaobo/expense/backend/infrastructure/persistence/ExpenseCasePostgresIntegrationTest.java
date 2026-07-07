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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ExpenseCasePostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:pg16")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("expense_flow")
                    .withUsername("expense")
                    .withPassword("expense");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ExpenseCaseApplicationService applicationService;
    @Autowired private ExpenseCaseRepository repository;
    @Autowired private JdbcClient jdbcClient;

    @Test
    void flywayCreatesAllCoreTables() {
        Integer tableCount =
                jdbcClient
                        .sql(
                                """
                                SELECT count(*)::int
                                FROM information_schema.tables
                                WHERE table_schema = 'public'
                                  AND table_name LIKE 'expense_%'
                                """)
                        .query(Integer.class)
                        .single();

        assertThat(tableCount).isEqualTo(13);
    }

    @Test
    void persistsLoadsAndTransitionsWithOptimisticLocking() {
        ExpenseCase created =
                applicationService.create(
                        new CreateExpenseCaseCommand(
                                "keycloak-user-1",
                                "Alice",
                                "CN-SH-RD",
                                "Client visit",
                                new BigDecimal("128.50"),
                                "CNY"));

        ExpenseCase loaded = applicationService.getOwned(created.id(), "keycloak-user-1");
        ExpenseCase uploaded =
                applicationService.transition(created.id(), ExpenseCaseStatus.UPLOADED);

        assertThat(loaded).isEqualTo(created);
        assertThat(uploaded.version()).isEqualTo(1);
        assertThat(uploaded.status()).isEqualTo(ExpenseCaseStatus.UPLOADED);

        ExpenseCase staleChange =
                created.transitionTo(ExpenseCaseStatus.UPLOADED, Instant.now());
        assertThatThrownBy(() -> repository.update(staleChange, 0))
                .isInstanceOf(ExpenseFlowException.class)
                .satisfies(
                        error ->
                                assertThat(((ExpenseFlowException) error).code())
                                        .isEqualTo(
                                                ExpenseFlowErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void enforcesOwnerIsolation() {
        ExpenseCase created =
                applicationService.create(
                        new CreateExpenseCaseCommand(
                                "keycloak-user-a",
                                "Alice",
                                "CN-SH-RD",
                                "Team dinner",
                                new BigDecimal("300.00"),
                                "CNY"));

        assertThatThrownBy(() -> applicationService.getOwned(created.id(), "keycloak-user-b"))
                .isInstanceOf(ExpenseFlowException.class)
                .satisfies(
                        error ->
                                assertThat(((ExpenseFlowException) error).code())
                                        .isEqualTo(ExpenseFlowErrorCode.ACCESS_DENIED));
    }
}
