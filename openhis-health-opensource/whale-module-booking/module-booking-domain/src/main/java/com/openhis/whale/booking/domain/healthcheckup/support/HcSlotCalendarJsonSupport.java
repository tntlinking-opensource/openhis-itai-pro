/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisContractTypeCodes;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 体检号源模板 JSON 解析（capacity_json / campus_codes_json / rest_weekdays）。
 */
public final class HcSlotCalendarJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HcSlotCalendarJsonSupport() {
    }

    /**
     * 解析各合同类型默认容量；空 JSON 时返回四类预置且容量均为 0。
     *
     * @param capacityJson 容量 JSON
     * @return 合同类型 → 默认容量
     */
    public static Map<String, Integer> parseCapacityMap(String capacityJson) {
        if (capacityJson == null || capacityJson.isBlank()) {
            return HcSlotCalendarJsonSupport.defaultCapacityMap();
        }
        try {
            Map<String, Integer> parsed = OBJECT_MAPPER.readValue(capacityJson, new TypeReference<Map<String, Integer>>() {
            });
            if (parsed == null || parsed.isEmpty()) {
                return HcSlotCalendarJsonSupport.defaultCapacityMap();
            }
            Map<String, Integer> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : parsed.entrySet()) {
                int value = 0;
                if (entry.getValue() != null) {
                    value = entry.getValue();
                }
                normalized.put(entry.getKey(), value);
            }
            return normalized;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "capacityJson 格式非法");
        }
    }

    /**
     * 容量 Map 序列化为 JSON。
     *
     * @param capacityMap 容量 Map
     * @return JSON 字符串
     */
    public static String writeCapacityMap(Map<String, Integer> capacityMap) {
        if (capacityMap == null || capacityMap.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(capacityMap);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "capacityJson 序列化失败");
        }
    }

    /**
     * 解析校区编码列表 JSON。
     *
     * @param campusCodesJson 校区 JSON
     * @return 校区编码列表
     */
    public static List<String> parseCampusCodes(String campusCodesJson) {
        if (campusCodesJson == null || campusCodesJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> codes = OBJECT_MAPPER.readValue(campusCodesJson, new TypeReference<List<String>>() {
            });
            if (codes == null) {
                return new ArrayList<>();
            }
            return codes;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "campusCodesJson 格式非法");
        }
    }

    /**
     * 校区编码列表序列化为 JSON。
     *
     * @param campusCodes 校区编码列表
     * @return JSON 字符串
     */
    public static String writeCampusCodes(List<String> campusCodes) {
        if (campusCodes == null || campusCodes.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(campusCodes);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "campusCodesJson 序列化失败");
        }
    }

    /**
     * 解析模板休息日 ISO 1-7 集合。
     *
     * @param restWeekdays 逗号分隔字符串
     * @return 休息日集合
     */
    public static Set<Integer> parseRestWeekdays(String restWeekdays) {
        Set<Integer> result = new LinkedHashSet<>();
        if (restWeekdays == null || restWeekdays.isBlank()) {
            return result;
        }
        String[] parts = restWeekdays.split(",");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            Integer day = mapIsoWeekdayToken(part.trim());
            if (day == null) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "restWeekdays 格式非法");
            }
            result.add(day);
        }
        return result;
    }

    /**
     * 将 ISO 星期 token（1–7）映射为整数；非法返回 null。
     *
     * @param token 单段字符串
     * @return 1–7 或 null
     */
    private static Integer mapIsoWeekdayToken(String token) {
        if ("1".equals(token)) {
            return 1;
        }
        if ("2".equals(token)) {
            return 2;
        }
        if ("3".equals(token)) {
            return 3;
        }
        if ("4".equals(token)) {
            return 4;
        }
        if ("5".equals(token)) {
            return 5;
        }
        if ("6".equals(token)) {
            return 6;
        }
        if ("7".equals(token)) {
            return 7;
        }
        return null;
    }

    /**
     * 休息日集合序列化为逗号分隔字符串。
     *
     * @param restWeekdays 休息日集合
     * @return 逗号分隔字符串
     */
    public static String writeRestWeekdays(Set<Integer> restWeekdays) {
        if (restWeekdays == null || restWeekdays.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Integer weekday : restWeekdays) {
            if (weekday == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(weekday);
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    /**
     * 预置四类合同类型默认容量 Map（均为 0）。
     *
     * @return 默认容量 Map
     */
    public static Map<String, Integer> defaultCapacityMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(PeisContractTypeCodes.STUDENT, 0);
        map.put(PeisContractTypeCodes.STAFF, 0);
        map.put(PeisContractTypeCodes.ENTERPRISE, 0);
        map.put(PeisContractTypeCodes.WALK_IN, 0);
        return map;
    }
}
