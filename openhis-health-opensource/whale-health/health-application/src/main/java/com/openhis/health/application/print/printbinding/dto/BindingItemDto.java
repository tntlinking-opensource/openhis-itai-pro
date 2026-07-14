/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printbinding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 场景绑定行 DTO（IF-BAS-PRT-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "业务场景模板绑定行")
public class BindingItemDto {

  /** 场景入口码。 */
  @Schema(description = "场景入口码", example = "outpatient.rx.print")
  private String bindingCode;

  /** 场景展示名称。 */
  @Schema(description = "场景展示名称", example = "门诊处方打印")
  private String name;

  /** 当前生效模板 busNo。 */
  @Schema(description = "当前生效模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 是否租户 override。 */
  @Schema(description = "是否租户 override", example = "true")
  private Boolean tenantOverride;
}
