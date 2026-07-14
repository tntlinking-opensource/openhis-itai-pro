/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

  /** 列表主键。 */
  @Schema(description = "列表主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  /** 模板业务键。 */
  @Schema(description = "模板业务键", example = "PT20260101001")
  private String busNo;

  /** 业务场景码。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 展示名称。 */
  @Schema(description = "展示名称", example = "门诊处方 A5")
  private String name;

  /** 聚合展示态。 */
  @Schema(description = "聚合展示态", example = "published")
  private String status;

  /** 当前 published 版本号。 */
  @Schema(description = "当前 published 版本号", example = "2")
  private Integer version;

  /** PrintJson schema 版本号。 */
  @Schema(description = "PrintJson schema 版本号", example = "1")
  private Integer schemaVersion;

  /** 是否存在租户草稿。 */
  @Schema(description = "是否存在租户草稿", example = "true")
  private Boolean hasDraft;

  /** 草稿行主键。 */
  @Schema(description = "草稿行主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long draftId;

  /** published 行主键。 */
  @Schema(description = "published 行主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long publishedId;

  /** 当前租户是否可删除。 */
  @Schema(description = "当前租户是否可删除", example = "true")
  private Boolean deletable;
}
