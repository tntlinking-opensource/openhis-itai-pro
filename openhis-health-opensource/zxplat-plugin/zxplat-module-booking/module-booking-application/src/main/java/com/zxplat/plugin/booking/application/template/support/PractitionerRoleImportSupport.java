/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.template.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.application.template.response.SlotProvisionTemplateItemDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 岗位出诊时段 JSON → T1 时段项。
 */
public final class PractitionerRoleImportSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PractitionerRoleImportSupport() {
    }

    /**
     * 解析岗位 availability/timePeriods JSON 为 T1 时段行。
     */
    public static List<SlotProvisionTemplateItemDto> parseTimePeriods(String timePeriodsJson) {
        if (StringUtils.isBlank(timePeriodsJson)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "岗位出诊时段为空");
        }
        try {
            List<SlotProvisionTemplateItemDto> direct = TemplateJsonSupport.parseSlotItems(timePeriodsJson);
            if (!direct.isEmpty()) {
                return direct;
            }
        } catch (BusinessException ignored) {
            // 尝试 health 结构解析
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(timePeriodsJson.trim());
            List<SlotProvisionTemplateItemDto> items = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    SlotProvisionTemplateItemDto item = parseNode(node);
                    if (item != null) {
                        items.add(item);
                    }
                }
            } else {
                SlotProvisionTemplateItemDto item = parseNode(root);
                if (item != null) {
                    items.add(item);
                }
            }
            if (items.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "岗位出诊时段格式无法识别");
            }
            return items;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "岗位出诊时段格式非法");
        }
    }

    private static SlotProvisionTemplateItemDto parseNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String start = readText(node, "startTime", "start_time");
        String end = readText(node, "endTime", "end_time");
        if (StringUtils.isBlank(start) || StringUtils.isBlank(end)) {
            return null;
        }
        SlotProvisionTemplateItemDto dto = new SlotProvisionTemplateItemDto();
        dto.setStartTime(normalizeTime(start));
        dto.setEndTime(normalizeTime(end));
        JsonNode capacityNode = node.get("capacity");
        if (capacityNode != null && capacityNode.isNumber()) {
            dto.setCapacity(capacityNode.intValue());
        } else {
            dto.setCapacity(1);
        }
        return dto;
    }

    private static String readText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && StringUtils.isNotBlank(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static String normalizeTime(String timeText) {
        if (timeText.length() >= 8) {
            return timeText.substring(0, 8);
        }
        if (timeText.length() == 5) {
            return timeText + ":00";
        }
        return timeText;
    }
}
