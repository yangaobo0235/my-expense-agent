package com.yangaobo.expense.backend.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.policy.PolicyEmbeddingModel;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "expense.ai.embedding",
        name = "provider",
        havingValue = "dashscope")
public class DashScopePolicyEmbeddingModel implements PolicyEmbeddingModel {

    private final DashScopeEmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DashScopePolicyEmbeddingModel(
            DashScopeEmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        if (properties.dimensions() != 1024) {
            throw new IllegalStateException("当前 PGVector 表结构要求向量维度为 1024");
        }
    }

    @Override
    public float[] embed(String text) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE,
                    "启用 DashScope 向量模型时必须配置 EXPENSE_AI_EMBEDDING_API_KEY 或 DASHSCOPE_API_KEY");
        }
        try {
            String body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "model", properties.model(),
                                    "input", text,
                                    "dimensions", properties.dimensions()));
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(properties.baseUrl() + "/embeddings"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + properties.apiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable("百炼向量接口返回 HTTP " + response.statusCode());
            }
            JsonNode embedding = objectMapper.readTree(response.body()).at("/data/0/embedding");
            if (!embedding.isArray() || embedding.size() != properties.dimensions()) {
                throw unavailable("百炼向量接口返回了非预期维度");
            }
            float[] vector = new float[embedding.size()];
            for (int index = 0; index < embedding.size(); index++) {
                vector[index] = embedding.get(index).floatValue();
            }
            return vector;
        } catch (MyExpenseAgentException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("百炼向量请求被中断");
        } catch (Exception exception) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE,
                    "百炼向量服务调用失败：" + exception.getMessage());
        }
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    private static MyExpenseAgentException unavailable(String message) {
        return new MyExpenseAgentException(MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE, message);
    }
}
