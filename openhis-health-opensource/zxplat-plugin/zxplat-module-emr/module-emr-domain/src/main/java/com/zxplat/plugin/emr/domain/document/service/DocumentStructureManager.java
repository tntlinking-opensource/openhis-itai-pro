/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zxplat.plugin.emr.domain.document.model.StructureItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文书结构解析与按数据项名赋值。基于 Tiptap/ProseMirror JSON：根为 doc，子节点含 type、attrs（name、title、value）、content。
 */
@Service
@RequiredArgsConstructor
public class DocumentStructureManager {

    private static final String TYPE_DOC = "doc";
    private static final String ATTRS = "attrs";
    private static final String CONTENT = "content";
    private static final String TYPE = "type";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_ELEMENT_ID = "elementId";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_VALUE = "value";

    private final ObjectMapper objectMapper;

    /**
     * 解析 content JSON，收集指定类型的节点（如 region、section、dataElement），组装为结构树。
     *
     * @param contentJson     Tiptap doc JSON 字符串
     * @param firstLevelOnly  若 true 只取第一层匹配节点，不递归子节点
     * @param nodeTypes       要收集的 node type 集合，空表示收集 region、section、dataElement
     */
    public List<StructureItem> parseStructure(String contentJson, boolean firstLevelOnly, Set<String> nodeTypes) {
        Set<String> types;
        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            types = nodeTypes;
        } else {
            types = Set.of("region", "section", "dataElement");
        }
        List<StructureItem> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            if (root == null || !root.isObject() || !TYPE_DOC.equals(root.path(TYPE).asText(null))) {
                return result;
            }
            JsonNode content = root.get(CONTENT);
            if (content == null || !content.isArray()) {
                return result;
            }
            collectNodes(content, types, firstLevelOnly, 0, null, result);
        } catch (Exception e) {
            throw new RuntimeException("解析文书结构失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 按数据项名批量写入 value。遍历 JSON，对 {@code attrs.name} 或 {@code attrs.elementId}
     * 在 nameToValue 中的节点设置 attrs.value，返回新 JSON 字符串。
     */
    public String setValuesByNames(String contentJson, Map<String, Object> nameToValue) {
        if (contentJson == null || contentJson.isEmpty() || nameToValue == null || nameToValue.isEmpty()) {
            if (contentJson != null) {
                return contentJson;
            }
            return "{}";
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            if (root == null || !root.isObject()) {
                return contentJson;
            }
            JsonNode content = root.get(CONTENT);
            if (content != null && content.isArray()) {
                applyValuesToNodes(content, nameToValue);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("按数据项名赋值失败: " + e.getMessage(), e);
        }
    }

    private void collectNodes(JsonNode content, Set<String> nodeTypes, boolean firstLevelOnly, int depth, Integer pageNo, List<StructureItem> out) {
        for (JsonNode node : content) {
            if (!node.isObject()) continue;
            String type = node.path(TYPE).asText(null);
            if (type == null) continue;
            if (nodeTypes.contains(type)) {
                JsonNode attrs = node.get(ATTRS);
                String name = null;
                String title = null;
                if (attrs != null) {
                    name = attrs.path(ATTR_NAME).asText(null);
                    title = attrs.path(ATTR_TITLE).asText(null);
                }
                StructureItem item = new StructureItem(name, title, type, depth, pageNo, null);
                if (!firstLevelOnly && node.has(CONTENT) && node.get(CONTENT).isArray()) {
                    List<StructureItem> children = new ArrayList<>();
                    collectNodes(node.get(CONTENT), nodeTypes, false, depth + 1, pageNo, children);
                    item.setChildren(children);
                }
                out.add(item);
            } else if (node.has(CONTENT) && node.get(CONTENT).isArray()) {
                collectNodes(node.get(CONTENT), nodeTypes, firstLevelOnly, depth, pageNo, out);
            }
        }
    }

    private Object resolveValueForAttrs(ObjectNode attrs, Map<String, Object> nameToValue) {
        String name = attrs.path(ATTR_NAME).asText(null);
        if (name != null && nameToValue.containsKey(name)) {
            return nameToValue.get(name);
        }
        String elementId = attrs.path(ATTR_ELEMENT_ID).asText(null);
        if (elementId != null && nameToValue.containsKey(elementId)) {
            return nameToValue.get(elementId);
        }
        return null;
    }

    private void applyValuesToNodes(JsonNode content, Map<String, Object> nameToValue) {
        for (JsonNode node : content) {
            if (!node.isObject()) continue;
            JsonNode attrs = node.get(ATTRS);
            if (attrs != null && attrs.isObject()) {
                Object val = resolveValueForAttrs((ObjectNode) attrs, nameToValue);
                if (val != null) {
                    ((ObjectNode) attrs).putPOJO(ATTR_VALUE, val);
                }
            }
            if (node.has(CONTENT) && node.get(CONTENT).isArray()) {
                applyValuesToNodes(node.get(CONTENT), nameToValue);
            }
        }
    }

    /**
     * 从 Tiptap content JSON 提取指定数据项纯文本（如 chiefComplaint）。
     * 优先取数据元 attrs.value；否则收集子树 text，并去掉「主诉：」类前缀。
     * 解析失败或未命中时返回 null（列表摘要用，不抛错）。
     *
     * @param contentJson 文书正文 JSON
     * @param dataItemName 数据项名 / elementId，如 chiefComplaint
     * @return 去空白后的纯文本；无则 null
     */
    public String extractPlainTextByDataItemName(String contentJson, String dataItemName) {
        if (contentJson == null || contentJson.isEmpty()) {
            return null;
        }
        if (dataItemName == null || dataItemName.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode content = root.get(CONTENT);
            if (content == null || !content.isArray()) {
                return null;
            }
            String fromValue = findFirstMatchingValue(content, dataItemName);
            if (fromValue == null || fromValue.isEmpty()) {
                // 兼容部分模板 attrs.name 用中文「主诉」
                if ("chiefComplaint".equals(dataItemName)) {
                    fromValue = findFirstMatchingValue(content, "主诉");
                }
            }
            if (fromValue != null && !fromValue.isEmpty()) {
                return fromValue;
            }
            String fromText = findFirstMatchingSubtreeText(content, dataItemName);
            if (fromText == null || fromText.isEmpty()) {
                if ("chiefComplaint".equals(dataItemName)) {
                    fromText = findFirstMatchingSubtreeText(content, "主诉");
                }
            }
            return normalizeExtractedText(fromText, dataItemName);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 优先匹配数据元 / chip 的 attrs.value */
    private String findFirstMatchingValue(JsonNode nodes, String dataItemName) {
        if (nodes == null || !nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node == null || !node.isObject()) {
                continue;
            }
            JsonNode attrs = node.get(ATTRS);
            if (attrs != null && attrs.isObject() && attrsMatchDataItem(attrs, dataItemName)) {
                String value = attrs.path(ATTR_VALUE).asText(null);
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
            JsonNode child = node.get(CONTENT);
            if (child != null && child.isArray()) {
                String nested = findFirstMatchingValue(child, dataItemName);
                if (nested != null && !nested.isEmpty()) {
                    return nested;
                }
            }
        }
        return null;
    }

    /** 匹配段落/区域名后收集子孙 text 节点 */
    private String findFirstMatchingSubtreeText(JsonNode nodes, String dataItemName) {
        if (nodes == null || !nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node == null || !node.isObject()) {
                continue;
            }
            JsonNode attrs = node.get(ATTRS);
            if (attrs != null && attrs.isObject() && attrsMatchDataItem(attrs, dataItemName)) {
                StringBuilder sb = new StringBuilder();
                collectTextNodes(node, sb);
                String text = sb.toString().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
            JsonNode child = node.get(CONTENT);
            if (child != null && child.isArray()) {
                String nested = findFirstMatchingSubtreeText(child, dataItemName);
                if (nested != null && !nested.isEmpty()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean attrsMatchDataItem(JsonNode attrs, String dataItemName) {
        String name = attrs.path(ATTR_NAME).asText(null);
        if (dataItemName.equals(name)) {
            return true;
        }
        String elementId = attrs.path(ATTR_ELEMENT_ID).asText(null);
        return dataItemName.equals(elementId);
    }

    private void collectTextNodes(JsonNode node, StringBuilder out) {
        if (node == null || !node.isObject()) {
            return;
        }
        String type = node.path(TYPE).asText(null);
        if ("text".equals(type)) {
            String text = node.path("text").asText("");
            if (!text.isEmpty()) {
                out.append(text);
            }
        }
        // 固定文案节点可能把字放在 attrs.text
        JsonNode attrs = node.get(ATTRS);
        if (attrs != null && attrs.isObject()) {
            String fixed = attrs.path("text").asText(null);
            if (fixed != null && !fixed.isEmpty() && !"text".equals(type)) {
                out.append(fixed);
            }
        }
        JsonNode child = node.get(CONTENT);
        if (child != null && child.isArray()) {
            for (JsonNode c : child) {
                collectTextNodes(c, out);
            }
        }
    }

    /** 去掉「主诉：」等标签前缀；仅标签无正文时视为空 */
    private String normalizeExtractedText(String raw, String dataItemName) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("chiefComplaint".equals(dataItemName)) {
            if (text.startsWith("主诉：")) {
                text = text.substring("主诉：".length()).trim();
            } else if (text.startsWith("主诉:")) {
                text = text.substring("主诉:".length()).trim();
            }
        }
        if (text.isEmpty()) {
            return null;
        }
        return text;
    }
}
