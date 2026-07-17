package com.yangaobo.expense.backend.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ai.ChatModelClient;
import com.yangaobo.expense.backend.application.ai.ChatModelProperties;
import com.yangaobo.expense.backend.application.governance.DependencyCircuitBreaker;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleChatModelClient implements ChatModelClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ChatModelProperties properties;
    private final DependencyCircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client;

    public OpenAiCompatibleChatModelClient(
            ChatModelProperties properties,
            DependencyCircuitBreaker circuitBreaker,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
        Duration timeout = properties.getTimeout() == null ? Duration.ofSeconds(30) : properties.getTimeout();
        this.client =
                new OkHttpClient.Builder()
                        .connectTimeout(timeout)
                        .readTimeout(timeout)
                        .writeTimeout(timeout)
                        .build();
    }

    @Override
    public ChatCompletion complete(ChatRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw dependency("Chat model API key is not configured", null);
        }
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        RuntimeException lastFailure = null;
        long startedNanos = System.nanoTime();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String payload = payload(request);
                String response =
                        circuitBreaker.execute(
                                "chat:" + request.modelName(),
                                () -> call(payload));
                JsonNode root = objectMapper.readTree(response);
                JsonNode content = root.at("/choices/0/message/content");
                if (!content.isTextual()) {
                    throw dependency("Chat model response does not contain message content", null);
                }
                JsonNode usage = root.get("usage");
                int promptTokens =
                        usage == null
                                ? estimateTokens(request.systemPrompt() + request.userPrompt())
                                : usage.path("prompt_tokens").asInt(0);
                int completionTokens =
                        usage == null
                                ? estimateTokens(content.asText())
                                : usage.path("completion_tokens").asInt(0);
                return new ChatCompletion(
                        content.asText(),
                        promptTokens,
                        completionTokens,
                        elapsedMs(startedNanos),
                        attempt - 1);
            } catch (IOException exception) {
                lastFailure = dependency("Chat model request failed", exception);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? dependency("Chat model request failed", null) : lastFailure;
    }

    private String payload(ChatRequest request) throws IOException {
        return objectMapper.writeValueAsString(
                Map.of(
                        "model",
                        request.modelName(),
                        "temperature",
                        request.temperature(),
                        "max_tokens",
                        request.maxTokens(),
                        "response_format",
                        Map.of("type", "json_object"),
                        "messages",
                        List.of(
                                Map.of("role", "system", "content", request.systemPrompt()),
                                Map.of("role", "user", "content", request.userPrompt()))));
    }

    private String call(String payload) {
        String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        Request request =
                new Request.Builder()
                        .url(endpoint)
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .post(RequestBody.create(payload, JSON))
                        .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw dependency("Chat model returned HTTP " + response.code(), null);
            }
            return body;
        } catch (IOException exception) {
            throw dependency("Chat model request failed", exception);
        }
    }

    private static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private static MyExpenseAgentException dependency(String message, Exception cause) {
        return new MyExpenseAgentException(
                MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE,
                cause == null ? message : message + ": " + cause.getMessage());
    }
}
