/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printjob.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印准备结果 DTO（IF-BAS-PRT-31）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印准备结果")
public class PreparePrintResultDto {

  /** 运行时 traceId。 */
  @Schema(description = "运行时 traceId", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
  private String traceId;

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 业务场景码。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 模板版本号。 */
  @Schema(description = "模板版本号", example = "2")
  private Integer templateVersion;

  /** hiprint 版式 JSON。 */
  @Schema(description = "hiprint 版式 JSON")
  @JsonProperty("PrintJson")
  private Object printJson;

  /** 模板示例打印数据 JSON。 */
  @Schema(description = "模板示例打印数据 JSON")
  private Object samplePrintData;
}
