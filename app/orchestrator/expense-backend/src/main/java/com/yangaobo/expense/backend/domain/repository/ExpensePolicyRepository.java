package com.yangaobo.expense.backend.domain.repository;

import com.yangaobo.expense.backend.domain.model.ExpensePolicy;
import com.yangaobo.expense.backend.domain.model.PolicyChunk;
import java.time.LocalDate;
import java.util.List;

public interface ExpensePolicyRepository {

    ExpensePolicy insert(ExpensePolicy policy, List<PolicyChunk> chunks);

    List<PolicyCatalogEntry> listCatalog();

    List<PolicySearchMatch> search(
            float[] queryEmbedding,
            String category,
            String region,
            String employeeGrade,
            LocalDate expenseDate,
            int limit,
            double minimumScore);
}
