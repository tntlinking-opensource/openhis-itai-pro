package com.openhis.whale.report.application.pdf;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class BindPathResolver {

    private BindPathResolver() {
    }

    @SuppressWarnings("unchecked")
    public static Object resolve(Map<String, Object> root, String bindPath) {
        if (root == null || bindPath == null || bindPath.isBlank()) {
            return null;
        }
        String[] parts = bindPath.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    public static String asText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> c && c.isEmpty()) {
            return "";
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> asRowList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }
}
