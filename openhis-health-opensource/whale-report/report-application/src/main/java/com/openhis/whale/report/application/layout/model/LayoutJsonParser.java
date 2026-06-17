package com.openhis.whale.report.application.layout.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class LayoutJsonParser {

    private final ObjectMapper objectMapper;

    public LayoutJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LayoutDocument parse(String layoutJson) {
        try {
            LayoutDocument doc = objectMapper.readValue(layoutJson, LayoutDocument.class);
            if (doc.getPage() == null) {
                doc.setPage(new LayoutPage());
            }
            if (doc.getItems() == null) {
                doc.setItems(java.util.List.of());
            }
            return doc;
        } catch (Exception e) {
            throw new BusinessException("RPT_LAYOUT_PARSE_FAILED", "layoutJson 解析失败: " + e.getMessage(), e);
        }
    }
}
