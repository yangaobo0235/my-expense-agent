package com.yangaobo.expense.backend.application.extraction;

public record PreparedDocument(
        DocumentInputKind kind,
        String text,
        byte[] binary,
        String mediaType,
        int pageCount) {

    public PreparedDocument {
        binary = binary == null ? new byte[0] : binary.clone();
        text = text == null ? "" : text;
    }

    @Override
    public byte[] binary() {
        return binary.clone();
    }
}
