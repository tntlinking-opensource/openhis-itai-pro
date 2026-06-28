/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PrintConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * PrintJson 版式校验工具（DM-PRT-001 §3.2 · BR-PRT-003/008/009）。
 *
 * <p>纯函数：调用方传入 {@link ObjectMapper}；供领域写路径与应用层 Pre 共用同一校验口径。</p>
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
        // Pre：禁止脚本键（BR-PRT-008）。
        assertNoForbiddenKeys(root);
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

    private static void assertNoForbiddenKeys(JsonNode node) {
        // BR-PRT-008：递归扫描 JSON 树，命中禁止键即拒收整份版式。
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (PrintConstants.isForbiddenPrintJsonKey(key)) {
                    throw new BusinessException(
                        ErrorCodeConstants.PRT_INVALID,
                        "模板格式不合法：禁止键 " + key);
                }
                assertNoForbiddenKeys(entry.getValue());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                assertNoForbiddenKeys(child);
            }
        }
    }

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
        for (JsonNode element : printElements) {
            validatePrintElement(element);
        }
        validatePaperMode(panel);
    }

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

    private static void validatePrintElement(JsonNode element) {
        if (!element.isObject()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：元素须为对象");
        }
        JsonNode printElementType = element.get("printElementType");
        if (printElementType == null || !printElementType.isObject()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：缺少 printElementType");
        }
        JsonNode typeNode = printElementType.get("type");
        if (typeNode == null || typeNode.asText().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：元素 type 不能为空");
        }
        String type = typeNode.asText().trim();
        if (!PrintConstants.isAllowedPrintElementType(type)) {
            throw new BusinessException(
                ErrorCodeConstants.PRT_INVALID, "模板格式不合法：不支持的元素 type：" + type);
        }
    }
}
