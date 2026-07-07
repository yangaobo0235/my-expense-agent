package com.yangaobo.expense.backend.application.document;

import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class DocumentContentInspector {

    public DetectedDocumentType detect(byte[] content) {
        if (startsWith(content, new byte[] {'%', 'P', 'D', 'F', '-'})) {
            return DetectedDocumentType.PDF;
        }
        if (startsWith(
                content,
                new byte[] {
                    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'
                })) {
            return DetectedDocumentType.PNG;
        }
        if (content.length >= 3
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF) {
            return DetectedDocumentType.JPEG;
        }
        throw new ExpenseFlowException(
                ExpenseFlowErrorCode.DOCUMENT_REJECTED,
                "Only PDF, PNG, and JPEG documents are supported");
    }

    public String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
