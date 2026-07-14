/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printbinding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 绑定配置页可选模板候选 DTO（IF-BAS-PRT-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "绑定配置可选模板候选")
public class TemplateCandidateDto {

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 模板展示名称。 */
  @Schema(description = "模板展示名称", example = "门诊处方 A5")
  private String name;

  /** 模板状态：draft / published。 */
  @Schema(description = "模板状态：draft / published", example = "published")
  private String status;
}
