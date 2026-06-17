package com.openhis.whale.booking.adapter.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.booking.contracts.dto.PractitionerRoleScheduleItemDto;
import org.apache.commons.lang3.StringUtils;

/**
 * 解析 health PractitionerRole {@code availability_json} 为 booking 排班 DTO 字段。
 */
public final class PractitionerRoleScheduleJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PractitionerRoleScheduleJsonSupport() {
    }

    public static PractitionerRoleScheduleItemDto parseAvailabilityJson(String availabilityJson) {
        PractitionerRoleScheduleItemDto dto = new PractitionerRoleScheduleItemDto();
        if (StringUtils.isBlank(availabilityJson)) {
            return dto;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(availabilityJson.trim());
            if (root == null || root.isNull()) {
                return dto;
            }
            if (root.isArray()) {
                for (JsonNode node : root) {
                    PractitionerRoleScheduleItemDto first = fillFromNode(node);
                    if (first.getDayOfWeek() != null || StringUtils.isNotBlank(first.getTimePeriodsJson())) {
                        return first;
                    }
                }
                dto.setTimePeriodsJson(availabilityJson);
                return dto;
            }
            return fillFromNode(root);
        } catch (Exception ex) {
            dto.setTimePeriodsJson(availabilityJson);
            return dto;
        }
    }

    private static PractitionerRoleScheduleItemDto fillFromNode(JsonNode node) {
        PractitionerRoleScheduleItemDto dto = new PractitionerRoleScheduleItemDto();
        if (node == null || node.isNull()) {
            return dto;
        }
        JsonNode dayNode = node.get("dayOfWeek");
        if (dayNode == null || dayNode.isNull()) {
            dayNode = node.get("day_of_week");
        }
        if (dayNode != null && dayNode.isNumber()) {
            dto.setDayOfWeek(dayNode.intValue());
        }
        JsonNode periodsNode = node.get("timePeriodsJson");
        if (periodsNode == null || periodsNode.isNull()) {
            periodsNode = node.get("timePeriods");
        }
        if (periodsNode == null || periodsNode.isNull()) {
            periodsNode = node.get("time_periods");
        }
        if (periodsNode != null && !periodsNode.isNull()) {
            dto.setTimePeriodsJson(periodsNode.isTextual() ? periodsNode.asText() : periodsNode.toString());
        } else if (node.has("startTime") || node.has("endTime") || node.has("periods")) {
            dto.setTimePeriodsJson(node.toString());
        } else {
            dto.setTimePeriodsJson(node.toString());
        }
        return dto;
    }
}
