package com.openhis.whale.report.application.query.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.report.application.query.response.ReportColumnDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReportDefinitionColumnSupport {

    private static final TypeReference<List<ReportColumnDto>> COLUMN_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ReportDefinitionColumnSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ReportColumnDto> parseColumns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ReportColumnDto> columns = objectMapper.readValue(json, COLUMN_LIST_TYPE);
            return columns == null ? List.of() : columns;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public String toJson(List<ReportColumnDto> columns) {
        List<ReportColumnDto> safe = columns == null ? List.of() : columns;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception ignored) {
            return "[]";
        }
    }

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
