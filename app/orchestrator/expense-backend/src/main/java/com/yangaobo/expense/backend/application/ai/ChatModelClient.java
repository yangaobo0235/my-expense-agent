package com.yangaobo.expense.backend.application.ai;

import java.math.BigDecimal;
import java.util.List;

public interface ChatModelClient {

    ChatCompletion complete(ChatRequest request);

    record ChatRequest(
            String operation,
            String modelName,
            BigDecimal temperature,
            int maxTokens,
            String systemPrompt,
            String userPrompt) {}

    record ChatCompletion(
            String content,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            int retryCount) {

        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }

    record JsonInstruction(
            String field,
            String description,
            boolean required) {

        public static JsonInstruction required(String field, String description) {
            return new JsonInstruction(field, description, true);
        }

        public static JsonInstruction optional(String field, String description) {
            return new JsonInstruction(field, description, false);
        }
    }

    static String jsonResponseInstruction(List<JsonInstruction> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append("Return valid JSON only. Required JSON fields:\n");
        for (JsonInstruction field : fields) {
            builder.append("- ")
                    .append(field.field())
                    .append(field.required() ? " required: " : " optional: ")
                    .append(field.description())
                    .append('\n');
        }
        return builder.toString();
    }
}
