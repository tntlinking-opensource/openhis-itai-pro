/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

/**
 * 位置 {@code adm_location.type_json} 读写工具（纯函数，调用方传入 {@link ObjectMapper}）。
 *
 * <p>应用层 DTO 出参为 {@code typeExtensions} 结构化 Map，禁止对外透出原始 JSON 串（B.6.2）。</p>
 */
public final class LocationTypeJsonUtil {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private LocationTypeJsonUtil() {
    }

    /**
     * 解析位置 type_json 为扩展 Map。
     *
     * @param objectMapper Jackson 实例
     * @param typeJson     持久化 JSON
     * @return 扩展字段 Map；空白或解析失败时返回空 Map（不抛错，避免列表整页失败）
     */
    public static Map<String, Object> parseToMap(ObjectMapper objectMapper, String typeJson) {
        if (objectMapper == null || typeJson == null || typeJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(typeJson, MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return Collections.emptyMap();
            }
            return parsed;
        } catch (Exception ignored) {
            // 历史脏 JSON：降级为空扩展，由前端按无扩展展示
            return Collections.emptyMap();
        }
    }
}
