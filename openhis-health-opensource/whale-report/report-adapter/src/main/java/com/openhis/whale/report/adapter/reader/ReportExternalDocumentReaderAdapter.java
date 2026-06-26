/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.reader;

import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.report.contracts.reader.ReportExternalDocumentReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * D 类外系统报告出厂占位：院点接入 LIS/PACS Adapter 后替换或扩展本实现。
 */
@Component
@ConditionalOnClass(ReportExternalDocumentReader.class)
@ConditionalOnProperty(name = "openhis.modules.report.enabled", havingValue = "true")
public class ReportExternalDocumentReaderAdapter implements ReportExternalDocumentReader {

    /**
     * 出厂占位：院点接入 LIS/PACS 后替换为真实文档 URL。
     *
     * @param documentCode 文档类型编码
     * @param params       业务参数（回显于 paramsEcho）
     */
    @Override
    public Map<String, Object> resolve(String documentCode, Map<String, Object> params) {
        String code = trimCode(documentCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentCode", code);
        result.put("available", false);
        result.put("title", mapTitle(code));
        result.put("contentType", "application/pdf");
        result.put("documentUrl", "");
        result.put("message", "外系统报告 Adapter 待院点接入（documentCode=" + code + "）");
        if (params != null && !params.isEmpty()) {
            result.put("paramsEcho", new LinkedHashMap<>(params));
        }
        return result;
    }

    private static String trimCode(String documentCode) {
        String trimmed = StringUtils.trimToNull(documentCode);
        if (trimmed != null) {
            return trimmed;
        }
        return "";
    }

    private static String mapTitle(String code) {
        if (StringUtils.isEmpty(code)) {
            return "外系统报告";
        }
        // 出厂文档类型与展示名映射
        if ("lis_report".equals(code)) {
            return "检验报告";
        }
        if ("pacs_report".equals(code)) {
            return "检查报告";
        }
        if ("pathology_report".equals(code)) {
            return "病理报告";
        }
        if ("hc_report".equals(code)) {
            return "体检报告";
        }
        return code;
    }
}
