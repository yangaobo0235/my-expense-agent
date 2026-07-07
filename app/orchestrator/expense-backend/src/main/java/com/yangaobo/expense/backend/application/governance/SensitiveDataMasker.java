package com.yangaobo.expense.backend.application.governance;

import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {

    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-+/=]{12,}");
    private static final Pattern SECRET_PAIR =
            Pattern.compile("(?i)(secret|token|api[_-]?key|password)\\s*[:=]\\s*[^\\s,;\"'}]+");
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD =
            Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})(\\d{3}[0-9Xx])(?!\\d)");
    private static final Pattern BANK_CARD =
            Pattern.compile("(?<!\\d)(\\d{4})\\d{8,15}(\\d{4})(?!\\d)");
    private static final Pattern EMAIL =
            Pattern.compile("(?i)([A-Z0-9._%+-]{2})[A-Z0-9._%+-]*(@[A-Z0-9.-]+\\.[A-Z]{2,})");

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String masked = BEARER_TOKEN.matcher(value).replaceAll("Bearer ***");
        masked = SECRET_PAIR.matcher(masked).replaceAll("$1=***");
        masked = PHONE.matcher(masked).replaceAll(match -> maskMiddle(match.group(1), 3, 4));
        masked = ID_CARD.matcher(masked).replaceAll("$1********$3");
        masked = BANK_CARD.matcher(masked).replaceAll("$1********$2");
        masked = EMAIL.matcher(masked).replaceAll("$1***$2");
        return masked;
    }

    public Map<String, Object> maskMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> masked = new java.util.LinkedHashMap<>();
        input.forEach((key, value) -> masked.put(key, maskValue(value)));
        return Map.copyOf(masked);
    }

    @SuppressWarnings("unchecked")
    private Object maskValue(Object value) {
        if (value instanceof String text) {
            return mask(text);
        }
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> masked = new java.util.LinkedHashMap<>();
            map.forEach((key, nested) -> masked.put(String.valueOf(key), maskValue(nested)));
            return Map.copyOf(masked);
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> values = new java.util.ArrayList<>();
            iterable.forEach(item -> values.add(maskValue(item)));
            return List.copyOf(values);
        }
        return value;
    }

    private static String maskMiddle(String value, int prefix, int suffix) {
        if (value.length() <= prefix + suffix) {
            return "***";
        }
        return value.substring(0, prefix) + "****" + value.substring(value.length() - suffix);
    }
}
