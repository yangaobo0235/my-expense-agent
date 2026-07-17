package com.yangaobo.expense.backend.infrastructure.storage;

import com.yangaobo.expense.backend.application.storage.DocumentObjectStorage;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MinioDocumentObjectStorage implements DocumentObjectStorage {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MinioDocumentObjectStorage.class);

    private final MinioClient minioClient;
    private final String endpoint;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public MinioDocumentObjectStorage(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.endpoint = properties.endpoint();
        this.bucket = properties.bucket();
    }

    @Override
    public void put(
            String objectKey, String contentType, long contentLength, InputStream content) {
        try {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(content, contentLength, -1)
                            .build());
        } catch (Exception exception) {
            logStorageFailure("store", objectKey, exception);
            throw unavailable("Could not store expense document", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            logStorageFailure("remove", objectKey, exception);
            throw unavailable("Could not remove expense document", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            ensureBucket();
            try (InputStream input =
                    minioClient.getObject(
                            GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                return input.readAllBytes();
            }
        } catch (Exception exception) {
            logStorageFailure("read", objectKey, exception);
            throw unavailable("Could not read expense document", exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey, Duration validity) {
        try {
            ensureBucket();
            long seconds = Math.max(1, Math.min(validity.toSeconds(), 604_800));
            return URI.create(
                    minioClient.getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.GET)
                                    .bucket(bucket)
                                    .object(objectKey)
                                    .expiry((int) seconds, TimeUnit.SECONDS)
                                    .build()));
        } catch (Exception exception) {
            logStorageFailure("create preview URL", objectKey, exception);
            throw unavailable("Could not create document preview URL", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            boolean exists =
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady.set(true);
        }
    }

    private void logStorageFailure(String operation, String objectKey, Exception exception) {
        if (exception instanceof ErrorResponseException error) {
            LOGGER.error(
                    "MinIO {} failed: endpoint={}, bucket={}, objectKey={}, code={}, message={}, requestId={}",
                    operation,
                    endpoint,
                    bucket,
                    objectKey,
                    error.errorResponse().code(),
                    error.errorResponse().message(),
                    error.errorResponse().requestId(),
                    exception);
            return;
        }
        LOGGER.error(
                "MinIO {} failed: endpoint={}, bucket={}, objectKey={}, cause={}: {}",
                operation,
                endpoint,
                bucket,
                objectKey,
                exception.getClass().getName(),
                exception.getMessage(),
                exception);
    }

    private static MyExpenseAgentException unavailable(String message, Exception cause) {
        return new MyExpenseAgentException(
                MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE, message, cause);
    }
}
