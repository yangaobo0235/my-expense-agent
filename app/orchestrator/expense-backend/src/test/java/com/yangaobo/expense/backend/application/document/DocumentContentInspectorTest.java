package com.yangaobo.expense.backend.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.common.error.MyExpenseAgentException;
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
                .isInstanceOf(MyExpenseAgentException.class);
    }

    @Test
    void sha256IsStable() {
        byte[] content = "my-expense-agent".getBytes(StandardCharsets.UTF_8);
        assertThat(inspector.sha256(content))
                .isEqualTo(
                        "47daaac9f905fb4d72a48a040a2c62f716052ff7ee24def8ebae0b83b8022e00");
    }
}
