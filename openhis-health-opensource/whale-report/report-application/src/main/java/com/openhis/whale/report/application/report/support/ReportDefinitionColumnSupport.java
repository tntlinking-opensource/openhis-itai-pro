package com.openhis.whale.report.application.report.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.report.application.report.response.ReportColumnDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 列 JSON 解析与默认列构建（definition.default_columns_json）。
 */
@Component
public class ReportDefinitionColumnSupport {

    private static final TypeReference<List<ReportColumnDto>> COLUMN_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ReportDefinitionColumnSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析列 JSON 为 DTO 列表；空或非法 JSON 返回空列表。
     *
     * @param json default_columns_json 或 override columns_json
     * @return 列 DTO 列表；空或非法时空列表
     */
    public List<ReportColumnDto> parseColumns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ReportColumnDto> columns = objectMapper.readValue(json, COLUMN_LIST_TYPE);
            if (columns == null) {
                return List.of();
            }
            return columns;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 列 DTO 列表序列化为 JSON 字符串。
     *
     * @param columns 列列表，null 视为空
     * @return JSON 数组字符串，失败时 {@code []}
     */
    public String toJson(List<ReportColumnDto> columns) {
        List<ReportColumnDto> safe = List.of();
        if (columns != null) {
            safe = columns;
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    /**
     * 从 Loader 列生成最小 default_columns_json（仅 field/label）。
     *
     * @param loaderColumns Loader 返回的列定义
     * @return JSON 字符串，无列时 {@code []}
     */
    public String buildDefaultColumnsJson(List<ReportColumnDto> loaderColumns) {
        if (loaderColumns == null || loaderColumns.isEmpty()) {
            return "[]";
        }
        List<ReportColumnDto> minimal = new ArrayList<>();
        for (ReportColumnDto column : loaderColumns) {
            if (column == null || column.getField() == null || column.getField().isBlank()) {
                continue;
            }
            ReportColumnDto item = new ReportColumnDto();
            item.setField(column.getField());
            item.setLabel(column.getLabel());
            minimal.add(item);
        }
        return toJson(minimal);
    }
}
