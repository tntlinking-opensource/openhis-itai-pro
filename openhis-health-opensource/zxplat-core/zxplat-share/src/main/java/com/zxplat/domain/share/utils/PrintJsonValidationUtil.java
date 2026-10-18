/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PrintConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import java.util.Locale;

/**
 * PrintJson 版式校验工具（DM-PRT-001 §3.2 · BR-PRT-003）。
 *
 * <p>纯函数：调用方传入 {@link ObjectMapper}；供领域写路径与应用层 Pre 共用同一校验口径。
 * 元素 type / formatter 等 hiprint 扩展字段不做白名单或禁止键校验。</p>
 */
public final class PrintJsonValidationUtil {

    private PrintJsonValidationUtil() {
    }

    /**
     * 校验 PrintJson 文档；失败抛 {@link BusinessException} PRT-422。
     *
     * @param objectMapper Jackson 实例
     * @param printJson    版式 JSON 字符串
     * @throws BusinessException 结构不合法
     */
    public static void validate(ObjectMapper objectMapper, String printJson) {
        // Gate：非空。
        if (printJson == null || printJson.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：PrintJson 不能为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(printJson);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：JSON 解析失败");
        }
        if (!root.isObject()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：根节点须为对象");
        }
        // Pre：根字段 schemaVersion / engine（DM §3.2）。
        JsonNode schemaVersion = root.get("schemaVersion");
        if (schemaVersion == null || !schemaVersion.isNumber()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：schemaVersion 须为数字");
        }
        JsonNode engine = root.get("engine");
        if (engine == null || !PrintConstants.PRINT_JSON_ENGINE.equals(engine.asText())) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：engine 须为 Print");
        }
        JsonNode panels = root.get("panels");
        if (panels == null || !panels.isArray() || panels.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：panels 须为非空数组");
        }
        for (JsonNode panel : panels) {
            validatePanel(panel);
        }
    }

    /**
     * 从 PrintJson 根读取 schemaVersion，缺省为 1。
     *
     * @param objectMapper Jackson 实例
     * @param printJson    版式 JSON
     * @return schemaVersion
     */
    public static int resolveSchemaVersion(ObjectMapper objectMapper, String printJson) {
        try {
            JsonNode root = objectMapper.readTree(printJson);
            JsonNode schemaVersion = root.get("schemaVersion");
            if (schemaVersion != null && schemaVersion.isNumber()) {
                return schemaVersion.intValue();
            }
        } catch (Exception ignored) {
            // validate 阶段已保证结构合法；此处兜底。
        }
        return 1;
    }

    /**
     * 校验单个 panel：对象结构、数字宽高、printElements 数组及 paperMode。
     *
     * @param panel hiprint panel 节点
     * @throws BusinessException {@link ErrorCodeConstants#PRT_INVALID}
     */
    private static void validatePanel(JsonNode panel) {
        if (!panel.isObject()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：panel 须为对象");
        }
        JsonNode width = panel.get("width");
        JsonNode height = panel.get("height");
        if (width == null || !width.isNumber() || height == null || !height.isNumber()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：panel 宽高须为数字");
        }
        JsonNode printElements = panel.get("printElements");
        if (printElements == null || !printElements.isArray()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：printElements 须为数组");
        }
        validatePaperMode(panel);
    }

    /**
     * 校验 panel.paperMode（fixed/continuous）与 paperType 合法组合。
     *
     * @param panel hiprint panel 节点
     * @throws BusinessException {@link ErrorCodeConstants#PRT_INVALID}
     */
    private static void validatePaperMode(JsonNode panel) {
        JsonNode paperModeNode = panel.get("paperMode");
        if (paperModeNode != null && !paperModeNode.isNull()) {
            String paperMode = paperModeNode.asText("").trim().toLowerCase(Locale.ROOT);
            if (!paperMode.isEmpty()
                && !PrintConstants.PRINT_JSON_PAPER_MODE_FIXED.equals(paperMode)
                && !PrintConstants.PRINT_JSON_PAPER_MODE_CONTINUOUS.equals(paperMode)) {
                throw new BusinessException(
                    ErrorCodeConstants.PRT_INVALID, "模板格式不合法：paperMode 须为 fixed 或 continuous");
            }
        }
        JsonNode paperTypeNode = panel.get("paperType");
        String paperType = "";
        if (paperTypeNode != null) {
            paperType = paperTypeNode.asText("").trim();
        }
        boolean rollType = paperType.equalsIgnoreCase(PrintConstants.PRINT_JSON_PAPER_TYPE_ROLL)
            || paperType.toLowerCase(Locale.ROOT).startsWith(
                PrintConstants.PRINT_JSON_PAPER_TYPE_ROLL + "-");
        if (paperModeNode != null && !paperModeNode.isNull()) {
            String paperMode = paperModeNode.asText("").trim().toLowerCase(Locale.ROOT);
            // 卷纸须 continuous，避免 Host 分页裁切错误。
            if (rollType && PrintConstants.PRINT_JSON_PAPER_MODE_FIXED.equals(paperMode)) {
                throw new BusinessException(
                    ErrorCodeConstants.PRT_INVALID,
                    "模板格式不合法：卷纸 paperType 须配合 paperMode=continuous");
            }
            if (PrintConstants.PRINT_JSON_PAPER_MODE_CONTINUOUS.equals(paperMode)
                && !paperType.isEmpty() && !rollType) {
                throw new BusinessException(
                    ErrorCodeConstants.PRT_INVALID,
                    "模板格式不合法：paperMode=continuous 时 paperType 须为 roll 或 roll-*");
            }
        }
    }

}
