/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 病案首页 {@code extension_json} 读写工具（纯函数，调用方传入 {@link ObjectMapper}）。
 *
 * <p>扩展区存 Wave 1 未入标量列的键值（如费用、医保扩展字段）；出参 DTO 侧为 {@code Map} 结构化字段。</p>
 */
public final class HomepageExtensionJsonUtil {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private HomepageExtensionJsonUtil() {
    }

    /**
     * 解析 extension JSON 为 Map。
     *
     * @param objectMapper Jackson 实例
     * @param json 持久化 JSON
     * @return 扩展字段 Map；空白或解析失败时返回空 Map
     */
    public static Map<String, Object> parse(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            if (parsed == null) {
                return new HashMap<>();
            }
            return parsed;
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    /**
     * 序列化扩展 Map 为 JSON。
     *
     * @param objectMapper Jackson 实例
     * @param extension 扩展字段 Map
     * @return JSON 字符串；空 Map 或序列化失败时返回 {@code null}
     */
    public static String toJson(ObjectMapper objectMapper, Map<String, Object> extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(extension);
        } catch (Exception ex) {
            return null;
        }
    }
}
