package com.yangaobo.expense.backend.application.document;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPersistenceService {

    private final ExpenseDocumentRepository documentRepository;
    private final ExpenseCaseApplicationService caseApplicationService;

    public DocumentPersistenceService(
            ExpenseDocumentRepository documentRepository,
            ExpenseCaseApplicationService caseApplicationService) {
        this.documentRepository = documentRepository;
        this.caseApplicationService = caseApplicationService;
    }

    @Transactional
    public ExpenseDocument save(ExpenseCase expenseCase, ExpenseDocument document) {
        ExpenseDocument saved = documentRepository.insert(document);
        if (expenseCase.status() == ExpenseCaseStatus.DRAFT) {
            caseApplicationService.transition(expenseCase.id(), ExpenseCaseStatus.UPLOADED);
        }
        return saved;
    }
}
