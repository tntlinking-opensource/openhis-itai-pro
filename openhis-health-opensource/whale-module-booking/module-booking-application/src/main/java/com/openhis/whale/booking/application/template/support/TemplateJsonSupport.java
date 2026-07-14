/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.template.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.template.response.SlotProvisionTemplateItemDto;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * T1 时段 JSON 与结构化 DTO 互转。
 */
public final class TemplateJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TemplateJsonSupport() {
    }

    /**
     * JSON 字符串 → 时段项列表。
     */
    public static List<SlotProvisionTemplateItemDto> parseSlotItems(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<SlotProvisionTemplateItemDto> items = OBJECT_MAPPER.readValue(
                json, new TypeReference<List<SlotProvisionTemplateItemDto>>() {
                });
            if (items == null) {
                return new ArrayList<>();
            }
            return items;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "slotTemplates 格式非法");
        }
    }

    /**
     * 时段项列表 → JSON 字符串。
     */
    public static String writeSlotItems(List<SlotProvisionTemplateItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "slotTemplates 不能为空");
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "slotTemplates 序列化失败");
        }
    }
}
