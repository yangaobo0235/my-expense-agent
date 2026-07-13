package com.yangaobo.expense.backend.application.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExpenseExtractionJsonSchema {

    public List<String> validate(JsonNode root) {
        List<String> errors = new ArrayList<>();
        requireText(root, "documentType", errors);
        requireText(root, "sellerName", errors);
        requireText(root, "currency", errors);
        requireNumber(root, "totalAmount", errors);
        requireNumber(root, "confidence", errors);
        JsonNode items = root.get("items");
        if (items == null || !items.isArray()) {
            errors.add("items must be an array");
        } else {
            for (int index = 0; index < items.size(); index++) {
                JsonNode item = items.get(index);
                requireText(item, "description", errors, "items[" + index + "]");
                requireNumber(item, "quantity", errors, "items[" + index + "]");
                requireNumber(item, "unitPrice", errors, "items[" + index + "]");
                requireNumber(item, "amount", errors, "items[" + index + "]");
            }
        }
        JsonNode warnings = root.get("warnings");
        if (warnings != null && !warnings.isArray()) {
            errors.add("warnings must be an array");
        }
        return List.copyOf(errors);
    }

    public String schemaText() {
        return """
                {
                  "documentType": "INVOICE",
                  "invoiceNumber": "INV-2026-001",
                  "sellerName": "南京青奥酒店",
                  "buyerName": "江南大学",
                  "issueDate": "2026-06-01",
                  "totalAmount": 580.00,
                  "currency": "CNY",
                  "items": [
                    {"description": "竞赛住宿费", "quantity": 1, "unitPrice": 580.00, "amount": 580.00}
                  ],
                  "confidence": 0.91,
                  "warnings": []
                }
                """;
    }

    private static void requireText(JsonNode root, String field, List<String> errors) {
        requireText(root, field, errors, "");
    }

    private static void requireText(JsonNode root, String field, List<String> errors, String prefix) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            errors.add(label(prefix, field) + " must be a non-empty string");
        }
    }

    private static void requireNumber(JsonNode root, String field, List<String> errors) {
        requireNumber(root, field, errors, "");
    }

    private static void requireNumber(JsonNode root, String field, List<String> errors, String prefix) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isNumber()) {
            errors.add(label(prefix, field) + " must be a number");
        }
    }

    private static String label(String prefix, String field) {
        return prefix == null || prefix.isBlank() ? field : prefix + "." + field;
    }
}
