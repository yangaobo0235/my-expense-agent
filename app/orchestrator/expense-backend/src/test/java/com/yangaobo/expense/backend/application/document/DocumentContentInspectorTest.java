package com.yangaobo.expense.backend.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentContentInspectorTest {

    private final DocumentContentInspector inspector = new DocumentContentInspector();

    @Test
    void detectsSupportedMagicNumbers() {
        assertThat(inspector.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(DetectedDocumentType.PDF);
        assertThat(
                        inspector.detect(
                                new byte[] {
                                    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'
                                }))
                .isEqualTo(DetectedDocumentType.PNG);
        assertThat(inspector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                .isEqualTo(DetectedDocumentType.JPEG);
    }

    @Test
    void rejectsExtensionSpoofingContent() {
        assertThatThrownBy(
                        () ->
                                inspector.detect(
                                        "not a real invoice".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExpenseFlowException.class);
    }

    @Test
    void sha256IsStable() {
        byte[] content = "expense-flow".getBytes(StandardCharsets.UTF_8);
        assertThat(inspector.sha256(content))
                .isEqualTo(
                        "cf63811b4449d6b63e06971fae4d9b66bf207166d2090ee6a326d0dcf608c07c");
    }
}
