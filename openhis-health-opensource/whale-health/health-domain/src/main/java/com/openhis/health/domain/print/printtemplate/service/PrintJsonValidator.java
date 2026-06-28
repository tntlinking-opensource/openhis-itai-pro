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

package com.openhis.health.domain.print.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * PrintJson 版式校验器（BR-PRT-003、008、009）。
 */
@Component
public class PrintJsonValidator {

  private final ObjectMapper objectMapper;

  public PrintJsonValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 校验 PrintJson 文档；失败抛 {@link BusinessException} PRT-422。
   *
   * @param printJson 版式 JSON 字符串
   */
  public void validate(String printJson) {
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
    JsonNode panels = root.get("panels");
    if (panels == null || !panels.isArray() || panels.isEmpty()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：panels 须为非空数组");
    }
    for (JsonNode panel : panels) {
      this.validatePanel(panel);
    }
  }

  private void validatePanel(JsonNode panel) {
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
      this.validatePrintElement(element);
    }
    this.validatePaperMode(panel);
  }

  private void validatePaperMode(JsonNode panel) {
    JsonNode paperModeNode = panel.get("paperMode");
    if (paperModeNode != null && !paperModeNode.isNull()) {
      String paperMode = paperModeNode.asText("").trim().toLowerCase();
      if (!paperMode.isEmpty()
          && !"fixed".equals(paperMode)
          && !"continuous".equals(paperMode)) {
        throw new BusinessException(
            ErrorCodeConstants.PRT_INVALID, "模板格式不合法：paperMode 须为 fixed 或 continuous");
      }
    }
    JsonNode paperTypeNode = panel.get("paperType");
    String paperType = paperTypeNode == null ? "" : paperTypeNode.asText("").trim();
    boolean rollType =
        paperType.equalsIgnoreCase("roll") || paperType.toLowerCase().startsWith("roll-");
    if (paperModeNode != null && !paperModeNode.isNull()) {
      String paperMode = paperModeNode.asText("").trim().toLowerCase();
      if (rollType && "fixed".equals(paperMode)) {
        throw new BusinessException(
            ErrorCodeConstants.PRT_INVALID,
            "模板格式不合法：卷纸 paperType 须配合 paperMode=continuous");
      }
      if ("continuous".equals(paperMode) && !paperType.isEmpty() && !rollType) {
        throw new BusinessException(
            ErrorCodeConstants.PRT_INVALID,
            "模板格式不合法：paperMode=continuous 时 paperType 须为 roll 或 roll-*");
      }
    }
  }

  private void validatePrintElement(JsonNode element) {
    if (!element.isObject()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：元素须为对象");
    }
  // Pre：元素须声明 printElementType.type。
    JsonNode printElementType = element.get("printElementType");
    if (printElementType == null || !printElementType.isObject()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：缺少 printElementType");
    }
    JsonNode typeNode = printElementType.get("type");
    if (typeNode == null || typeNode.asText().trim().isEmpty()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板格式不合法：元素 type 不能为空");
    }
  }

  /**
   * 从 PrintJson 根读取 schemaVersion，缺省为 1。
   *
   * @param printJson 版式 JSON
   * @return schemaVersion
   */
  public int resolveSchemaVersion(String printJson) {
    try {
      JsonNode root = objectMapper.readTree(printJson);
      JsonNode schemaVersion = root.get("schemaVersion");
      if (schemaVersion != null && schemaVersion.isNumber()) {
        return schemaVersion.intValue();
      }
    } catch (Exception ignored) {
      // 校验阶段已保证结构合法；此处兜底。
    }
    return 1;
  }
}
