package com.openhis.whale.report.application.report.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.report.application.report.response.ReportFilterFieldDto;
import com.openhis.whale.report.application.report.response.ReportFilterSelectOptionDto;
import com.openhis.whale.report.application.report.response.ReportFooterConfigDto;
import com.openhis.whale.report.application.report.response.ReportHeaderConfigDto;
import com.openhis.whale.report.application.report.response.ReportHeaderLineDto;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设计器 JSON 列 → 结构化 DTO（仅用于库表 JSON 读出后的装配，出参禁止透传 JSON 字符串）。
 */
public final class ReportDesignJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

    private ReportDesignJsonSupport() {
    }

    /**
     * 解析表头设计 JSON。
     *
     * @param json 库表 {@code header_json}
     * @return 结构化表头；空或非法时返回空对象（非 null）
     */
    public static ReportHeaderConfigDto parseHeaderConfig(String json) {
        Map<String, Object> raw = ReportHeaderSupport.parseJsonMap(json);
        ReportHeaderConfigDto dto = new ReportHeaderConfigDto();
        if (raw.isEmpty()) {
            return dto;
        }
        dto.setTitle(stringValue(raw.get("title")));
        dto.setSubtitle(stringValue(raw.get("subtitle")));
        List<Map<String, Object>> lineMaps = ReportHeaderSupport.normalizeLines(raw);
        List<ReportHeaderLineDto> lines = new ArrayList<>();
        for (Map<String, Object> line : lineMaps) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            ReportHeaderLineDto row = new ReportHeaderLineDto();
            row.setType(stringValue(line.get("type")));
            row.setText(stringValue(line.get("text")));
            row.setAlign(stringValue(line.get("align")));
            row.setLeft(stringValue(line.get("left")));
            row.setRight(stringValue(line.get("right")));
            lines.add(row);
        }
        dto.setLines(lines);
        return dto;
    }

    /**
     * 解析表底设计 JSON（{@code notes} + {@code lines[]}）。
     *
     * @param json 库表 {@code footer_json}
     * @return 结构化表底；空或非法时返回空对象
     */
    public static ReportFooterConfigDto parseFooterConfig(String json) {
        Map<String, Object> raw = ReportHeaderSupport.parseJsonMap(json);
        ReportFooterConfigDto dto = new ReportFooterConfigDto();
        if (raw.isEmpty()) {
            return dto;
        }
        dto.setNotes(stringValue(raw.get("notes")));
        dto.setLines(parseStringLines(raw.get("lines")));
        return dto;
    }

    private static List<String> parseStringLines(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                lines.add(String.valueOf(item));
            }
        }
        return lines;
    }

    /**
     * 解析筛选器 JSON；空时返回默认日期区间字段。
     *
     * @param json 库表 {@code filter_json}
     * @return 筛选字段列表，永不为 null
     */
    public static List<ReportFilterFieldDto> parseFilters(String json) {
        if (!StringUtils.isNotEmpty(json)) {
            return defaultDateFilters();
        }
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json.trim(), LIST_MAP_TYPE);
            if (raw == null || raw.isEmpty()) {
                return defaultDateFilters();
            }
            List<ReportFilterFieldDto> result = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                ReportFilterFieldDto field = new ReportFilterFieldDto();
                field.setField(stringValue(item.get("field")));
                field.setLabel(stringValue(item.get("label")));
                field.setType(stringValue(item.get("type")));
                field.setOptions(parseSelectOptions(item.get("options")));
                result.add(field);
            }
            if (result.isEmpty()) {
                return defaultDateFilters();
            }
            return result;
        } catch (Exception ignored) {
            return defaultDateFilters();
        }
    }

    private static List<ReportFilterFieldDto> defaultDateFilters() {
        List<ReportFilterFieldDto> list = new ArrayList<>();
        list.add(filterField("dateFrom", "开始日期", "date"));
        list.add(filterField("dateTo", "结束日期", "date"));
        return list;
    }

    private static ReportFilterFieldDto filterField(String field, String label, String type) {
        ReportFilterFieldDto dto = new ReportFilterFieldDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setType(type);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private static List<ReportFilterSelectOptionDto> parseSelectOptions(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ReportFilterSelectOptionDto> options = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                ReportFilterSelectOptionDto option = new ReportFilterSelectOptionDto();
                option.setLabel(stringValue(map.get("label")));
                option.setValue(stringValue(map.get("value")));
                options.add(option);
            }
        }
        return options;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}
