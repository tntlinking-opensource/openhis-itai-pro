/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 病案首页 {@code field_sources_json} 读写工具（纯函数，调用方传入 {@link ObjectMapper}）。
 */
public final class FieldSourcesJsonUtil {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private FieldSourcesJsonUtil() {
    }

    /**
     * 解析 JSON 为字段来源 Map。
     *
     * @param objectMapper Jackson 实例
     * @param json 持久化 JSON
     * @return 字段来源 Map；解析失败或空串时返回空 Map
     */
    public static Map<String, String> parse(ObjectMapper objectMapper, String json) {
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
     * 序列化字段来源 Map 为 JSON。
     *
     * @param objectMapper Jackson 实例
     * @param sources 字段来源 Map
     * @return JSON 字符串；空 Map 或序列化失败时返回 {@code null}
     */
    public static String toJson(ObjectMapper objectMapper, Map<String, String> sources) {
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
