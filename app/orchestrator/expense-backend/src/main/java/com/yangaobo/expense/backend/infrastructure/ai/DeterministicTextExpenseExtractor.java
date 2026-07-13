package com.yangaobo.expense.backend.infrastructure.ai;

import com.yangaobo.expense.backend.application.document.DocumentContentInspector;
import com.yangaobo.expense.backend.application.extraction.DocumentInputKind;
import com.yangaobo.expense.backend.application.extraction.ExpenseDocumentExtractor;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.application.extraction.ExtractionCandidate;
import com.yangaobo.expense.backend.application.extraction.PreparedDocument;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline extractor for labelled text fixtures. Production deployments should
 * use the LLM extractor and keep this mode explicit for local validation only.
 */
@Component
@ConditionalOnProperty(
        prefix = "expense.extraction",
        name = "mode",
        havingValue = "deterministic",
        matchIfMissing = false)
public class DeterministicTextExpenseExtractor implements ExpenseDocumentExtractor {

    public static final String PROMPT_VERSION = "deterministic-text-v1";

    private static final Pattern TYPE = field("document(?:Type| type)|单据类型");
    private static final Pattern INVOICE_CODE = field("invoiceCode|invoice code|发票代码");
    private static final Pattern INVOICE_NUMBER = field("invoiceNumber|invoice number|发票号码");
    private static final Pattern SELLER = field("sellerName|seller|销售方|商户");
    private static final Pattern BUYER = field("buyerName|buyer|购买方");
    private static final Pattern DATE = field("issueDate|issue date|开票日期|日期");
    private static final Pattern TOTAL = field("totalAmount|total amount|总金额|价税合计|合计");
    private static final Pattern CURRENCY = field("currency|币种");

    private final DocumentContentInspector hasher;

    public DeterministicTextExpenseExtractor(DocumentContentInspector hasher) {
        this.hasher = hasher;
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    @Override
    public ExtractionCandidate extract(PreparedDocument prepared) {
        if (prepared.kind() != DocumentInputKind.TEXT) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Vision extraction is not configured yet");
        }
        String text = prepared.text();
        ExtractedExpenseDocument document =
                new ExtractedExpenseDocument(
                        value(TYPE, text, "INVOICE"),
                        value(INVOICE_CODE, text, null),
                        value(INVOICE_NUMBER, text, null),
                        value(SELLER, text, null),
                        value(BUYER, text, null),
                        date(value(DATE, text, null)),
                        decimal(value(TOTAL, text, null)),
                        value(CURRENCY, text, "CNY").toUpperCase(),
                        List.of(),
                        0.65,
                        List.of("Parsed by deterministic labelled-text extractor"));
        return new ExtractionCandidate(
                document,
                "deterministic-text-parser",
                PROMPT_VERSION,
                hasher.sha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static Pattern field(String aliases) {
        return Pattern.compile(
                "(?im)^(?:"
                        + aliases
                        + ")\\s*[:：]\\s*([^\\r\\n]+?)\\s*$",
                Pattern.CASE_INSENSITIVE);
    }

    private static String value(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private static BigDecimal decimal(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9.\\-]", "");
        try {
            return normalized.isBlank() ? null : new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().replace('/', '-').replace('.', '-'));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
