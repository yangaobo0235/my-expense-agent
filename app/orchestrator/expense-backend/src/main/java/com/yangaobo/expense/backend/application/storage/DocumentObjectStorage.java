package com.yangaobo.expense.backend.application.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface DocumentObjectStorage {

    void put(String objectKey, String contentType, long contentLength, InputStream content);

    void delete(String objectKey);

    byte[] get(String objectKey);

    URI presignedGetUrl(String objectKey, Duration validity);
}
