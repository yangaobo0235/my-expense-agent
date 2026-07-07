package com.yangaobo.expense.account.application;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AccountApplicationService {

    private final JdbcClient jdbcClient;

    public AccountApplicationService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public EmployeeProfile getEmployeeProfile(String employeeId) {
        String id = required(employeeId, "employeeId");
        return jdbcClient
                .sql(
                        """
                        SELECT employee_id, name, department_code, employee_grade, region,
                               available_balance, currency
                        FROM expense_account_employee
                        WHERE employee_id = :employeeId
                        """)
                .param("employeeId", id)
                .query(
                        (rs, rowNum) ->
                                new EmployeeProfile(
                                        rs.getString("employee_id"),
                                        rs.getString("name"),
                                        rs.getString("department_code"),
                                        rs.getString("employee_grade"),
                                        rs.getString("region"),
                                        paymentMethods(id),
                                        rs.getBigDecimal("available_balance"),
                                        rs.getString("currency")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("员工不存在：" + id));
    }

    public List<String> getPaymentMethods(String employeeId) {
        getEmployeeProfile(employeeId);
        return paymentMethods(employeeId);
    }

    public AccountBalance getAccountBalance(String employeeId) {
        EmployeeProfile profile = getEmployeeProfile(employeeId);
        return new AccountBalance(
                profile.employeeId(), profile.availableBalance(), profile.currency());
    }

    private List<String> paymentMethods(String employeeId) {
        return jdbcClient
                .sql(
                        """
                        SELECT payment_method
                        FROM expense_account_payment_method
                        WHERE employee_id = :employeeId
                        ORDER BY payment_method
                        """)
                .param("employeeId", employeeId)
                .query(String.class)
                .list();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    public record EmployeeProfile(
            String employeeId,
            String name,
            String departmentCode,
            String employeeGrade,
            String region,
            List<String> paymentMethods,
            BigDecimal availableBalance,
            String currency) {

        public EmployeeProfile {
            paymentMethods = List.copyOf(paymentMethods);
        }
    }

    public record AccountBalance(String employeeId, BigDecimal available, String currency) {}
}
