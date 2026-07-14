/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printtemplate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 画布打印模板详情 DTO（设计态加载 / 保存后返回，IF-BAS-PRT-13～14）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "画布打印模板详情")
public class PrtTemplateDto {

  /** 模板物理行主键。 */
  @Schema(description = "模板物理行主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  /** 模板业务键。 */
  @Schema(description = "模板业务键", example = "PT20260101001")
  private String busNo;

  /** 业务场景码。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 模板展示名称。 */
  @Schema(description = "模板展示名称", example = "门诊处方 A5")
  private String name;

  /** 行状态：draft / published / archived。 */
  @Schema(description = "行状态：draft / published / archived", example = "draft")
  private String status;

  /** 模板业务版本号。 */
  @Schema(description = "模板业务版本号", example = "1")
  private Integer version;

  /** PrintJson schema 版本号。 */
  @Schema(description = "PrintJson schema 版本号", example = "1")
  private Integer schemaVersion;

  /** hiprint 版式 JSON。 */
  @Schema(description = "hiprint 版式 JSON")
  @JsonProperty("PrintJson")
  private Object printJson;

  /** 设计器示例打印数据 JSON。 */
  @Schema(description = "设计器示例打印数据 JSON")
  private Object samplePrintData;
}
