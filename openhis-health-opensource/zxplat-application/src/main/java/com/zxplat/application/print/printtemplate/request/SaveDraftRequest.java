/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printtemplate.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 保存草稿请求（IF-BAS-PRT-14）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存画布打印模板草稿请求")
public class SaveDraftRequest {

  /** 已有草稿主键。 */
  @Schema(description = "已有草稿主键", example = "1234567890123456789")
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

  /** hiprint 版式 JSON。 */
  @Schema(description = "hiprint 版式 JSON")
  @JsonProperty("PrintJson")
  private Object printJson;

  /** 示例打印数据 JSON。 */
  @Schema(description = "示例打印数据 JSON")
  private Object samplePrintData;
}
