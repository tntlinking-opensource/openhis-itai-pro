/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.whale.emr.domain.document.model.StructureItem;
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
}
