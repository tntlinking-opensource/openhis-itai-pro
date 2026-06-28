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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 画布打印模板列表行 DTO（按 busNo 聚合后的逻辑模板视图，IF-BAS-PRT-11）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "画布打印模板列表行")
public class PrtTemplateListItemDto {

  /** 列表主键：优先 draft 行 id，否则 published/archived 行 id。 */
  @Schema(description = "列表主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  /** 模板业务键。 */
  @Schema(description = "模板业务键", example = "PT20260101001")
  private String busNo;

  /** 业务场景码；draft 优先于 published。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 展示名称；draft 优先于 published。 */
  @Schema(description = "展示名称", example = "门诊处方 A5")
  private String name;

  /** 聚合展示态：{@code draft} / {@code published} / {@code archived}。 */
  @Schema(description = "聚合展示态", example = "published")
  private String status;

  /** 当前生效 published 版本号；无 published 时为 null。 */
  @Schema(description = "当前 published 版本号", example = "2")
  private Integer version;

  /** PrintJson schema 版本。 */
  @Schema(description = "PrintJson schema 版本号", example = "1")
  private Integer schemaVersion;

  /** 是否存在租户 draft 行。 */
  @Schema(description = "是否存在租户草稿", example = "true")
  private Boolean hasDraft;

  /** 租户 draft 行主键；无 draft 时为 null。 */
  @Schema(description = "草稿行主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long draftId;

  /** 最新 published 行主键；无 published 时为 null。 */
  @Schema(description = "published 行主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long publishedId;

  /** 当前租户是否可删除（出厂平台模板为 false）。 */
  @Schema(description = "当前租户是否可删除", example = "true")
  private Boolean deletable;
}
