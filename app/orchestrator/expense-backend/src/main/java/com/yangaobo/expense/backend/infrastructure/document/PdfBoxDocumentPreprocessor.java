package com.yangaobo.expense.backend.infrastructure.document;

import com.yangaobo.expense.backend.application.extraction.DocumentInputKind;
import com.yangaobo.expense.backend.application.extraction.DocumentPreprocessor;
import com.yangaobo.expense.backend.application.extraction.PreparedDocument;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxDocumentPreprocessor implements DocumentPreprocessor {

    static final int MIN_MEANINGFUL_TEXT_CHARACTERS = 40;
    static final int MAX_PDF_PAGES = 20;

    @Override
    public PreparedDocument prepare(byte[] content, String contentType) {
        if (!"application/pdf".equals(contentType)) {
            return new PreparedDocument(DocumentInputKind.IMAGE, "", content, contentType, 1);
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1 || pageCount > MAX_PDF_PAGES) {
                throw rejected("PDF must contain between 1 and 20 pages");
            }
            String text = normalize(new PDFTextStripper().getText(document));
            if (meaningfulCharacters(text) >= MIN_MEANINGFUL_TEXT_CHARACTERS) {
                return new PreparedDocument(
                        DocumentInputKind.TEXT, text, new byte[0], contentType, pageCount);
            }
            return new PreparedDocument(
                    DocumentInputKind.IMAGE, "", content, contentType, pageCount);
        } catch (IOException exception) {
            ExpenseFlowException failure = rejected("PDF is damaged or cannot be parsed");
            failure.initCause(exception);
            throw failure;
        }
    }

    private static String normalize(String text) {
        return text.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static long meaningfulCharacters(String text) {
        return text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }

    private static ExpenseFlowException rejected(String message) {
        return new ExpenseFlowException(ExpenseFlowErrorCode.DOCUMENT_REJECTED, message);
    }
}
