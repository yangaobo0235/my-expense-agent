package com.yangaobo.expense.account.interfaces.rest;

import com.yangaobo.expense.account.application.AccountApplicationService;
import com.yangaobo.expense.account.application.AccountApplicationService.AccountBalance;
import com.yangaobo.expense.account.application.AccountApplicationService.EmployeeProfile;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
public class AccountController {

    private final AccountApplicationService service;

    public AccountController(AccountApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{employeeId}")
    public EmployeeProfile profile(@PathVariable String employeeId) {
        return service.getEmployeeProfile(employeeId);
    }

    @GetMapping("/{employeeId}/payment-methods")
    public List<String> paymentMethods(@PathVariable String employeeId) {
        return service.getPaymentMethods(employeeId);
    }

    @GetMapping("/{employeeId}/balance")
    public AccountBalance balance(@PathVariable String employeeId) {
        return service.getAccountBalance(employeeId);
    }
}
