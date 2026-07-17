package com.yangaobo.expense.backend.application.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class EvaluationDatasetLoader {

    private EvaluationDatasetLoader() {}

    static <T> T load(
            ObjectMapper objectMapper,
            String location,
            String bundleNodeName,
            Class<T> type,
            String errorPrefix) {
        try {
            byte[] bytes = readBytes(location);
            JsonNode root = objectMapper.readTree(bytes);
            JsonNode bundled = root.path("evaluation").path(bundleNodeName);
            JsonNode dataset = bundled.isMissingNode() ? root : bundled;
            return objectMapper.treeToValue(dataset, type);
        } catch (IOException exception) {
            throw unavailable(errorPrefix + "：" + location + "：" + exception.getMessage());
        }
    }

    static byte[] datasetBytes(
            ObjectMapper objectMapper,
            String location,
            String bundleNodeName,
            String errorPrefix) {
        try {
            byte[] bytes = readBytes(location);
            JsonNode root = objectMapper.readTree(bytes);
            JsonNode bundled = root.path("evaluation").path(bundleNodeName);
            if (bundled.isMissingNode()) {
                return bytes;
            }
            return objectMapper.writeValueAsBytes(bundled);
        } catch (IOException exception) {
            throw unavailable(errorPrefix + "：" + location + "：" + exception.getMessage());
        }
    }

    private static byte[] readBytes(String location) throws IOException {
        if (!location.startsWith("classpath:")) {
            return Files.readAllBytes(Path.of(location).toAbsolutePath().normalize());
        }
        String resourceName = location.substring("classpath:".length());
        try (InputStream stream =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IOException("classpath resource not found");
            }
            return stream.readAllBytes();
        }
    }

    private static MyExpenseAgentException unavailable(String message) {
        return new MyExpenseAgentException(MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE, message);
    }
}
