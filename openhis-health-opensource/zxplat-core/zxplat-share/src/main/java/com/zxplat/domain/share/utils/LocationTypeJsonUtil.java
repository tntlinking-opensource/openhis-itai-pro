/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.share.constants.LocationTypeJsonConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 位置 {@code adm_location.type_json} 读写工具（纯函数，调用方传入 {@link ObjectMapper}）。
 *
 * <p>应用层 DTO 出参为 {@code typeExtensions} / {@code servedOrganizationIds} 等结构化字段，
 * 禁止对外透出原始 JSON 串（B.6.2）。</p>
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

    /**
     * 读取病区 {@code servedOrganizationIds}（多科挂靠附加列表）。
     *
     * @param objectMapper Jackson 实例
     * @param typeJson     持久化 JSON
     * @return 去重保序的科室主键；无键或解析失败时 empty
     */
    public static List<Long> readServedOrganizationIds(ObjectMapper objectMapper, String typeJson) {
        Map<String, Object> map = parseToMap(objectMapper, typeJson);
        Object raw = map.get(LocationTypeJsonConstants.SERVED_ORGANIZATION_IDS);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (Object element : list) {
            Long id = coerceToLong(element);
            if (id != null) {
                ordered.add(id);
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 将服务科室列表写入 type_json（保留其它扩展键）。
     *
     * @param objectMapper           Jackson 实例
     * @param existingTypeJson       原 JSON，可空
     * @param servedOrganizationIds  服务科室主键；空则移除该键
     * @return 可持久化的 JSON 字符串
     */
    public static String writeServedOrganizationIds(
        ObjectMapper objectMapper,
        String existingTypeJson,
        List<Long> servedOrganizationIds) {
        if (objectMapper == null) {
            return LocationTypeJsonConstants.EMPTY_TYPE_JSON;
        }
        Map<String, Object> map = new LinkedHashMap<>(parseToMap(objectMapper, existingTypeJson));
        if (servedOrganizationIds == null || servedOrganizationIds.isEmpty()) {
            map.remove(LocationTypeJsonConstants.SERVED_ORGANIZATION_IDS);
        } else {
            LinkedHashSet<Long> ordered = new LinkedHashSet<>();
            for (Long id : servedOrganizationIds) {
                if (id != null) {
                    ordered.add(id);
                }
            }
            if (ordered.isEmpty()) {
                map.remove(LocationTypeJsonConstants.SERVED_ORGANIZATION_IDS);
            } else {
                map.put(LocationTypeJsonConstants.SERVED_ORGANIZATION_IDS, new ArrayList<>(ordered));
            }
        }
        if (map.isEmpty()) {
            return LocationTypeJsonConstants.EMPTY_TYPE_JSON;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception ignored) {
            return LocationTypeJsonConstants.EMPTY_TYPE_JSON;
        }
    }

    /**
     * 判定病区是否服务指定住院科室：主挂靠 {@code organizationId} 或 type_json 附加列表命中。
     *
     * @param objectMapper     Jackson 实例
     * @param primaryOrganizationId 病区主挂靠科室
     * @param typeJson         病区 type_json
     * @param organizationId   待匹配科室
     * @return true 表示该科可在入院「科室下病区」中选到此病区
     */
    public static boolean wardServesOrganization(
        ObjectMapper objectMapper,
        Long primaryOrganizationId,
        String typeJson,
        Long organizationId) {
        if (organizationId == null) {
            return false;
        }
        if (organizationId.equals(primaryOrganizationId)) {
            return true;
        }
        List<Long> served = readServedOrganizationIds(objectMapper, typeJson);
        return served.contains(organizationId);
    }

    /**
     * 将 JSON 元素尽量转为 Long（兼容 Number / 数字字符串）。
     *
     * @param element 列表元素
     * @return 主键；无法解析时 null
     */
    private static Long coerceToLong(Object element) {
        if (element == null) {
            return null;
        }
        if (element instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(element).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
