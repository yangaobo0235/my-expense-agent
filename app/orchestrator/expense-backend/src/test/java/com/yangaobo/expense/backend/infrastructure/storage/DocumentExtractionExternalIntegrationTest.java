package com.yangaobo.expense.backend.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.application.CreateExpenseCaseCommand;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.document.DocumentUploadService;
import com.yangaobo.expense.backend.application.document.DocumentQueryService;
import com.yangaobo.expense.backend.application.extraction.ExpenseExtractionService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
class DocumentExtractionExternalIntegrationTest {

    @Autowired private ExpenseCaseApplicationService caseService;
    @Autowired private DocumentUploadService uploadService;
    @Autowired private ExpenseExtractionService extractionService;
    @Autowired private DocumentQueryService documentQueryService;
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
    void extractsTextPdfAndPersistsValidatedJson() throws Exception {
        ExpenseCase expenseCase =
                caseService.create(
                        new CreateExpenseCaseCommand(
                                "integration-extraction-user",
                                "Extraction Integration",
                                "TEST",
                                "Text PDF extraction",
                                new BigDecimal("288.00"),
                                "CNY"));
        createdCaseId = expenseCase.id();
        ExpenseDocument document =
                uploadService.upload(
                        expenseCase.id(),
                        expenseCase.ownerSubject(),
                        new MockMultipartFile(
                                "file",
                                "invoice.pdf",
                                "application/pdf",
                                invoicePdf()));
        createdObjectKey = document.objectKey();

        var result = extractionService.extract(expenseCase.id(), expenseCase.ownerSubject());

        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().getFirst().validationErrors()).isEmpty();
        assertThat(result.documents().getFirst().result().sellerName())
                .isEqualTo("ExpenseFlow Hotel");
        assertThat(result.documents().getFirst().result().totalAmount())
                .isEqualByComparingTo("288.00");
        assertThat(caseService.getOwned(expenseCase.id(), expenseCase.ownerSubject()).status())
                .isEqualTo(ExpenseCaseStatus.EXTRACTED);
        var views =
                documentQueryService.list(
                        expenseCase.id(), expenseCase.ownerSubject(), false);
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().extraction().result().sellerName())
                .isEqualTo("ExpenseFlow Hotel");
        var previewResponse =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(views.getFirst().previewUrl()).GET().build(),
                                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(previewResponse.statusCode()).isEqualTo(200);
        assertThat(previewResponse.body()).isNotEmpty();

        String storedJson =
                jdbcClient
                        .sql(
                                "SELECT extraction_result::text FROM expense_document WHERE id = :id")
                        .param("id", document.id())
                        .query(String.class)
                        .single();
        assertThat(storedJson).contains("ExpenseFlow Hotel", "\"totalAmount\": 288.00");
    }

    private static byte[] invoicePdf() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                stream.newLineAtOffset(50, 750);
                for (String line :
                        new String[] {
                            "documentType: INVOICE",
                            "invoiceCode: 3100",
                            "invoiceNumber: ABC123456",
                            "sellerName: ExpenseFlow Hotel",
                            "buyerName: Example Company",
                            "issueDate: 2026-06-17",
                            "totalAmount: 288.00",
                            "currency: CNY"
                        }) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -18);
                }
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
