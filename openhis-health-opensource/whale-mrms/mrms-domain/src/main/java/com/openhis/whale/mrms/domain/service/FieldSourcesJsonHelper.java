/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * field_sources_json 读写辅助。
 */
@Component
@RequiredArgsConstructor
public class FieldSourcesJsonHelper {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * 解析 JSON 为 Map。
     *
     * @param json 持久化 JSON
     * @return 字段来源 Map
     */
    public Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(json, MAP_TYPE);
            if (parsed == null) {
                return new HashMap<>();
            }
            return parsed;
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    /**
     * 序列化为 JSON 字符串。
     *
     * @param sources 字段来源 Map
     * @return JSON 字符串
     */
    public String toJson(Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception ex) {
            return null;
        }
    }
}
