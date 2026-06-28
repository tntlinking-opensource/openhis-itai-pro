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

  /** 模板物理行主键（draft 或 published 行 id）。 */
  @Schema(description = "模板物理行主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  /** 模板业务键，租户内唯一。 */
  @Schema(description = "模板业务键", example = "PT20260101001")
  private String busNo;

  /** 业务场景码（如 {@code outpatient.rx.print}）；可为空。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 模板展示名称。 */
  @Schema(description = "模板展示名称", example = "门诊处方 A5")
  private String name;

  /** 行状态：{@code draft} / {@code published} / {@code archived}。 */
  @Schema(description = "行状态：draft / published / archived", example = "draft")
  private String status;

  /** 模板业务版本（非乐观锁列）。 */
  @Schema(description = "模板业务版本号", example = "1")
  private Integer version;

  /** PrintJson schema 版本号。 */
  @Schema(description = "PrintJson schema 版本号", example = "1")
  private Integer schemaVersion;

  /** hiprint 版式 JSON（结构化对象出参）。 */
  @Schema(description = "hiprint 版式 JSON")
  @JsonProperty("PrintJson")
  private Object printJson;

  /** 设计器示例打印数据 JSON（结构化对象出参）。 */
  @Schema(description = "设计器示例打印数据 JSON")
  private Object samplePrintData;
}
