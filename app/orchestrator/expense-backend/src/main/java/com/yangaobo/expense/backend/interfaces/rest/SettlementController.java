package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.settlement.ExpenseSettlementService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expense-cases")
public class SettlementController {

    private final ExpenseSettlementService settlementService;

    public SettlementController(
            ExpenseSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/{caseId}/settlement")
    public ExpenseSettlementService.SettlementResult settle(
            @PathVariable UUID caseId,
            @Valid @RequestBody SettlementRequest request,
            Principal principal) {
        return settlementService.settle(
                caseId, request.requestId(), principal.getName());
    }
}
