package com.yangaobo.expense.backend.application.extraction;

public interface DocumentPreprocessor {

    PreparedDocument prepare(byte[] content, String contentType);
}
