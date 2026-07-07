package com.yangaobo.expense.backend.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.application.CreateExpenseCaseCommand;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.document.DocumentUploadService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EXPENSE_IT_MINIO_ENDPOINT", matches = ".+")
class DocumentUploadExternalIntegrationTest {

    @Autowired private ExpenseCaseApplicationService caseService;
    @Autowired private DocumentUploadService uploadService;
    @Autowired private MinioClient minioClient;
    @Autowired private MinioProperties minioProperties;
    @Autowired private JdbcClient jdbcClient;

    private UUID createdCaseId;
    private String createdObjectKey;

    @DynamicPropertySource
    static void externalProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("EXPENSE_IT_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> required("EXPENSE_IT_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> required("EXPENSE_IT_DATABASE_PASSWORD"));
        registry.add("expense.minio.endpoint", () -> required("EXPENSE_IT_MINIO_ENDPOINT"));
        registry.add("expense.minio.access-key", () -> required("EXPENSE_IT_MINIO_ACCESS_KEY"));
        registry.add("expense.minio.secret-key", () -> required("EXPENSE_IT_MINIO_SECRET_KEY"));
        registry.add(
                "expense.minio.bucket",
                () -> System.getenv().getOrDefault("EXPENSE_IT_MINIO_BUCKET", "expense-documents"));
    }

    @AfterEach
    void cleanup() throws Exception {
        if (createdObjectKey != null) {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(createdObjectKey)
                            .build());
        }
        if (createdCaseId != null) {
            jdbcClient
                    .sql("DELETE FROM expense_case WHERE id = :id")
                    .param("id", createdCaseId)
                    .update();
        }
    }

    @Test
    void uploadsToMinioAndPersistsDocument() throws Exception {
        ExpenseCase expenseCase =
                caseService.create(
                        new CreateExpenseCaseCommand(
                                "integration-minio-user",
                                "MinIO Integration",
                                "TEST",
                                "MinIO round trip",
                                new BigDecimal("66.00"),
                                "CNY"));
        createdCaseId = expenseCase.id();

        ExpenseDocument document =
                uploadService.upload(
                        expenseCase.id(),
                        expenseCase.ownerSubject(),
                        new MockMultipartFile(
                                "file",
                                "smoke.pdf",
                                "application/pdf",
                                "%PDF-1.7 ExpenseFlow smoke".getBytes()));
        createdObjectKey = document.objectKey();

        var object =
                minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(minioProperties.bucket())
                                .object(document.objectKey())
                                .build());

        assertThat(object.size()).isEqualTo(document.fileSize());
        assertThat(caseService.getOwned(expenseCase.id(), expenseCase.ownerSubject()).status())
                .isEqualTo(ExpenseCaseStatus.UPLOADED);
        Integer documentCount =
                jdbcClient
                        .sql(
                                "SELECT count(*)::int FROM expense_document WHERE case_id = :caseId")
                        .param("caseId", expenseCase.id())
                        .query(Integer.class)
                        .single();
        assertThat(documentCount).isEqualTo(1);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
