package com.openhis.whale.booking.domain.template.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.template.queries.model.SlotProvisionItemModel;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * T1 时段 JSON 解析（领域层）。
 */
public final class TemplateSlotJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TemplateSlotJsonSupport() {
    }

    public static List<SlotProvisionItemModel> parseSlotItems(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<SlotProvisionItemModel> items = OBJECT_MAPPER.readValue(
                json, new TypeReference<List<SlotProvisionItemModel>>() {
                });
            return items != null ? items : new ArrayList<>();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "slotTemplates 格式非法");
        }
    }
}
