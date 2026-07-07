package com.yangaobo.expense.backend.application.document;

public enum DetectedDocumentType {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    private final String contentType;
    private final String extension;

    DetectedDocumentType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
