package com.yangaobo.expense.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class AccountApplicationServiceTest {

    private JdbcClient jdbcClient;
    private AccountApplicationService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:account_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        jdbcClient = JdbcClient.create(dataSource);
        jdbcClient
                .sql(
                        """
                        CREATE TABLE my_expense_agent_applicant (
                            applicant_id VARCHAR(80) PRIMARY KEY,
                            name VARCHAR(160) NOT NULL,
                            region VARCHAR(32) NOT NULL
                        );
                        CREATE TABLE my_expense_agent_reimbursement_account (
                            applicant_id VARCHAR(80) NOT NULL,
                            account_type VARCHAR(80) NOT NULL,
                            PRIMARY KEY (applicant_id, account_type)
                        );
                        CREATE TABLE my_expense_agent_project_member (
                            project_code VARCHAR(80) NOT NULL,
                            applicant_id VARCHAR(80) NOT NULL,
                            campus_role VARCHAR(32) NOT NULL,
                            PRIMARY KEY (project_code, applicant_id)
                        );
                        CREATE TABLE my_expense_agent_project_budget (
                            project_code VARCHAR(80) PRIMARY KEY,
                            total_amount NUMERIC(18,2) NOT NULL,
                            available_amount NUMERIC(18,2) NOT NULL,
                            currency VARCHAR(8) NOT NULL,
                            version BIGINT NOT NULL,
                            updated_at TIMESTAMP NOT NULL
                        );
                        CREATE TABLE my_expense_agent_budget_debit (
                            debit_id UUID PRIMARY KEY,
                            request_id VARCHAR(128) NOT NULL UNIQUE,
                            case_id UUID NOT NULL,
                            project_code VARCHAR(80) NOT NULL,
                            applicant_id VARCHAR(80) NOT NULL,
                            amount NUMERIC(18,2) NOT NULL,
                            currency VARCHAR(8) NOT NULL,
                            status VARCHAR(32) NOT NULL,
                            remaining_available NUMERIC(18,2) NOT NULL,
                            created_at TIMESTAMP NOT NULL
                        );
                        CREATE VIEW my_expense_agent_applicant_account AS
                        SELECT applicant.applicant_id,
                               applicant.name,
                               member.project_code,
                               member.campus_role AS campus_level,
                               applicant.region,
                               budget.available_amount AS budget_balance,
                               budget.currency
                        FROM my_expense_agent_project_member member
                        JOIN my_expense_agent_applicant applicant
                          ON applicant.applicant_id = member.applicant_id
                        JOIN my_expense_agent_project_budget budget
                          ON budget.project_code = member.project_code
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        INSERT INTO my_expense_agent_applicant VALUES ('student01', '李明', 'CN');
                        INSERT INTO my_expense_agent_reimbursement_account VALUES ('student01', 'CAMPUS_CARD');
                        INSERT INTO my_expense_agent_project_member VALUES ('CS-SRTP', 'student01', 'STUDENT');
                        INSERT INTO my_expense_agent_project_budget VALUES (
                            'CS-SRTP', 50000.00, 50000.00, 'CNY', 0, CURRENT_TIMESTAMP
                        )
                        """)
                .update();
        service = new AccountApplicationService(jdbcClient);
    }

    @Test
    void shouldResolveApplicantFromProjectMembership() {
        var profile = service.getApplicantProfile("student01", "CS-SRTP");

        assertThat(profile.projectCode()).isEqualTo("CS-SRTP");
        assertThat(profile.campusLevel()).isEqualTo("STUDENT");
        assertThat(profile.budgetBalance()).isEqualByComparingTo("50000.00");
        assertThat(profile.reimbursementAccounts()).containsExactly("CAMPUS_CARD");
    }

    @Test
    void shouldResolveReimbursementAccountsWithoutProjectFilter() {
        assertThat(service.getReimbursementAccounts("student01")).containsExactly("CAMPUS_CARD");
    }

    @Test
    void shouldDebitSharedBudgetExactlyOnceForReplay() {
        UUID caseId = UUID.randomUUID();

        var first =
                service.debitProjectBudget(
                        "budget-1",
                        caseId,
                        "CS-SRTP",
                        "student01",
                        new BigDecimal("1000.00"),
                        "CNY");
        var replay =
                service.debitProjectBudget(
                        "budget-1",
                        caseId,
                        "CS-SRTP",
                        "student01",
                        new BigDecimal("1000.00"),
                        "CNY");

        assertThat(replay.debitId()).isEqualTo(first.debitId());
        assertThat(replay.remainingAvailable()).isEqualByComparingTo("49000.00");
        assertThat(service.getProjectBudgetBalance("student01", "CS-SRTP").available())
                .isEqualByComparingTo("49000.00");
    }

    @Test
    void shouldRejectOverspendAndRequestIdPayloadChanges() {
        UUID caseId = UUID.randomUUID();
        service.debitProjectBudget(
                "budget-2",
                caseId,
                "CS-SRTP",
                "student01",
                new BigDecimal("1000.00"),
                "CNY");

        assertThatThrownBy(
                        () ->
                                service.debitProjectBudget(
                                        "budget-2",
                                        caseId,
                                        "CS-SRTP",
                                        "student01",
                                        new BigDecimal("1001.00"),
                                        "CNY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId");
        assertThatThrownBy(
                        () ->
                                service.debitProjectBudget(
                                        "budget-3",
                                        UUID.randomUUID(),
                                        "CS-SRTP",
                                        "student01",
                                        new BigDecimal("50000.00"),
                                        "CNY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不足");
    }
}
