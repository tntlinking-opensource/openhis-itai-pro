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

  /** prepare 签发的 traceId，confirm 须原样回传。 */
  @Schema(description = "运行时 traceId", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
  private String traceId;

  /** 解析到的模板 busNo。 */
  @Schema(description = "模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 业务场景码；按 busNo 解析时可能来自 published 行。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 使用的 published 模板版本号。 */
  @Schema(description = "模板版本号", example = "2")
  private Integer templateVersion;

  /** hiprint 版式 JSON（结构化对象出参）。 */
  @Schema(description = "hiprint 版式 JSON")
  @JsonProperty("PrintJson")
  private Object printJson;

  /** 模板示例打印数据（结构化对象出参）。 */
  @Schema(description = "模板示例打印数据 JSON")
  private Object samplePrintData;
}
