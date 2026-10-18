/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PrintJson 版式校验器（BR-PRT-003）。
 */
@Component
@RequiredArgsConstructor
public class PrintJsonValidator {

  private final ObjectMapper objectMapper;

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

  /**
   * 校验单个 panel：对象结构、数字宽高、printElements 数组及 paperMode。
   *
   * @param panel hiprint panel 节点
   * @throws BusinessException {@link ErrorCodeConstants#PRT_INVALID}
   */
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
    this.validatePaperMode(panel);
  }

  /**
   * 校验 panel.paperMode（fixed/continuous）与 paperType 合法组合。
   *
   * @param panel hiprint panel 节点
   * @throws BusinessException {@link ErrorCodeConstants#PRT_INVALID}
   */
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
    String paperType;
    if (paperTypeNode == null) {
      paperType = "";
    } else {
      paperType = paperTypeNode.asText("").trim();
    }
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
