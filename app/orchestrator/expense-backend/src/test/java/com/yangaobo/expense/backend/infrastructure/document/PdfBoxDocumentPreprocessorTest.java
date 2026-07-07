package com.yangaobo.expense.backend.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.application.extraction.DocumentInputKind;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

class PdfBoxDocumentPreprocessorTest {

    private final PdfBoxDocumentPreprocessor preprocessor =
            new PdfBoxDocumentPreprocessor();

    @Test
    void extractsMeaningfulTextPdf() throws Exception {
        byte[] pdf =
                textPdf(
                        "documentType: INVOICE invoiceNumber: ABC123456 "
                                + "sellerName: ExpenseFlow Hotel totalAmount: 288.00 "
                                + "currency: CNY issueDate: 2026-06-17");

        var prepared = preprocessor.prepare(pdf, "application/pdf");

        assertThat(prepared.kind()).isEqualTo(DocumentInputKind.TEXT);
        assertThat(prepared.text()).contains("ExpenseFlow Hotel");
        assertThat(prepared.pageCount()).isEqualTo(1);
    }

    @Test
    void routesImageOnlyPdfToVisionInput() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }

        var prepared = preprocessor.prepare(pdf, "application/pdf");

        assertThat(prepared.kind()).isEqualTo(DocumentInputKind.IMAGE);
        assertThat(prepared.binary()).isEqualTo(pdf);
    }

    @Test
    void routesUploadedImagesDirectlyToVisionInput() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};

        var prepared = preprocessor.prepare(png, "image/png");

        assertThat(prepared.kind()).isEqualTo(DocumentInputKind.IMAGE);
        assertThat(prepared.mediaType()).isEqualTo("image/png");
    }

    private static byte[] textPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                stream.newLineAtOffset(40, 750);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
