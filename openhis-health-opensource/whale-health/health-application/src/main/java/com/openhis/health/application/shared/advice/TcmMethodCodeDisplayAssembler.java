/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.Map;

/**
 * 中药用法 {@code tcm_method_code} 展示解析：中医行优先 tcm 字典，未命中回落 {@code method_code}。
 */
public final class TcmMethodCodeDisplayAssembler {

    /** Jackson 解析器（读取中药方头 content_json）。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具类禁止实例化。 */
    private TcmMethodCodeDisplayAssembler() {
    }

    /**
     * 解析用法展示名。
     *
     * @param tcmMethodLabelByCode     tcm_method_code 码表
     * @param westernMethodLabelByCode method_code 码表
     * @param tcmFlag                  是否中药饮片行
     * @param methodCode               持久化用法编码
     * @return 展示名；未命中时返回 {@code null}
     */
    public static String resolveMethodDisplayName(
        Map<String, String> tcmMethodLabelByCode,
        Map<String, String> westernMethodLabelByCode,
        Boolean tcmFlag,
        String methodCode) {
        if (StringUtils.isEmpty(methodCode)) {
            return null;
        }
        // 中药行：优先 tcm_method_code 字典，避免误用西医给药途径文案
        if (Boolean.TRUE.equals(tcmFlag)) {
            String tcmLabel = OrderCatalogDisplayAssembler.resolveCategoryDisplay(tcmMethodLabelByCode, methodCode);
            if (StringUtils.isNotEmpty(tcmLabel)) {
                return tcmLabel;
            }
        }
        // 非中药或未命中：回落 method_code 通用字典
        return OrderCatalogDisplayAssembler.resolveCategoryDisplay(westernMethodLabelByCode, methodCode);
    }

    /**
     * 从中药方头 {@code content_json} 读取文本字段（如 {@code decoctionMethodCode}）。
     *
     * @param contentJson 方头 JSON 字符串
     * @param fieldName   字段名
     * @return 文本值；解析失败或字段缺失时为 {@code null}
     */
    public static String readContentJsonTextField(String contentJson, String fieldName) {
        if (StringUtils.isEmpty(contentJson) || StringUtils.isEmpty(fieldName)) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                return null;
            }
            String text = node.asText(null);
            if (StringUtils.isEmpty(text)) {
                return null;
            }
            return text.trim();
        } catch (Exception ignored) {
            return null;
        }
    }
}
