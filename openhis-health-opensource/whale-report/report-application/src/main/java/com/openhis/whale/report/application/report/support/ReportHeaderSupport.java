package com.openhis.whale.report.application.report.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.report.domain.support.ReportTextSupport;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表表头 JSON 解析与占位符替换。
 *
 * <p>结构：title、subtitle、lines[]（type=text|split）。
 */
public final class ReportHeaderSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};

    private ReportHeaderSupport() {}

    /**
     * 解析表头 JSON 并替换 {@code {{placeholder}}} 占位符。
     *
     * @param headerJson    设计器保存的 header JSON
     * @param placeholders  键值对，如 dateRange、reportTitle
     * @return 结构化 header Map（title、subtitle、lines）
     */
    public static Map<String, Object> resolve(String headerJson, Map<String, String> placeholders) {
        Map<String, Object> raw = parseMap(headerJson);
        if (raw.isEmpty()) {
            return raw;
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("title", resolveText(stringValue(raw.get("title")), placeholders));
        resolved.put("subtitle", resolveText(stringValue(raw.get("subtitle")), placeholders));

        List<Map<String, Object>> lines = normalizeLines(raw);
        List<Map<String, Object>> resolvedLines = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            String type = stringValue(line.get("type"));
            if ("split".equalsIgnoreCase(type)) {
                // 左右分栏行
                Map<String, Object> split = new LinkedHashMap<>();
                split.put("type", "split");
                split.put("left", resolveText(stringValue(line.get("left")), placeholders));
                split.put("right", resolveText(stringValue(line.get("right")), placeholders));
                resolvedLines.add(split);
                continue;
            }
            Map<String, Object> textLine = new LinkedHashMap<>();
            textLine.put("type", "text");
            textLine.put("text", resolveText(stringValue(line.get("text")), placeholders));
            textLine.put("align", normalizeAlign(stringValue(line.get("align"))));
            resolvedLines.add(textLine);
        }
        resolved.put("lines", resolvedLines);

        // 兼容旧字段（前端仍可能读取）
        resolved.put("leftText", resolveText(stringValue(raw.get("leftText")), placeholders));
        resolved.put("rightText", resolveText(stringValue(raw.get("rightText")), placeholders));
        return resolved;
    }

    /**
     * 规范化 lines 数组；缺失时从 leftText/rightText 兼容字段推导。
     *
     * @param raw 解析后的 header Map
     * @return lines 列表，可能为空
     */
    public static List<Map<String, Object>> normalizeLines(Map<String, Object> raw) {
        List<Map<String, Object>> lines = parseLines(raw.get("lines"));
        if (!lines.isEmpty()) {
            return lines;
        }
        String left = stringValue(raw.get("leftText"));
        String right = stringValue(raw.get("rightText"));
        if (StringUtils.isNotEmpty(left) || StringUtils.isNotEmpty(right)) {
            // 旧版 leftText/rightText 转 split 行
            Map<String, Object> split = new LinkedHashMap<>();
            split.put("type", "split");
            split.put("left", left);
            split.put("right", right);
            return List.of(split);
        }
        return List.of();
    }

    private static List<Map<String, Object>> parseLines(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> line = new LinkedHashMap<>();
                    map.forEach((k, v) -> line.put(String.valueOf(k), v));
                    result.add(line);
                }
            }
            return result;
        }
        if (value instanceof String text && StringUtils.isNotEmpty(text)) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(text.trim(), LIST_TYPE);
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    /**
     * 解析任意 JSON 对象为 Map；非法或空串返回空 Map。
     *
     * @param json JSON 字符串
     * @return 键值 Map
     */
    public static Map<String, Object> parseJsonMap(String json) {
        return parseMap(json);
    }

    private static Map<String, Object> parseMap(String json) {
        if (!StringUtils.isNotEmpty(json)) {
            return Map.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(json.trim(), MAP_TYPE);
            if (map == null) {
                return Map.of();
            }
            return map;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String resolveText(String text, Map<String, String> placeholders) {
        if (!StringUtils.isNotEmpty(text) || placeholders == null || placeholders.isEmpty()) {
            return ReportTextSupport.nullToEmpty(text);
        }
        String resolved = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }

    private static String normalizeAlign(String align) {
        if (!StringUtils.isNotEmpty(align)) {
            return "left";
        }
        String value = align.trim().toLowerCase();
        return switch (value) {
            case "center", "right" -> value;
            default -> "left";
        };
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}
