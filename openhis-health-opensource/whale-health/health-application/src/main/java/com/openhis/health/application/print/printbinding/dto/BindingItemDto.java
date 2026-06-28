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

  /** 场景入口码（如 {@code outpatient.rx.print}）。 */
  @Schema(description = "场景入口码", example = "outpatient.rx.print")
  private String bindingCode;

  /** 场景展示名称（平台 seed 定义）。 */
  @Schema(description = "场景展示名称", example = "门诊处方打印")
  private String name;

  /** 当前生效模板 busNo（租户 override 或出厂默认）。 */
  @Schema(description = "当前生效模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 是否已被当前租户 override。 */
  @Schema(description = "是否租户 override", example = "true")
  private Boolean tenantOverride;
}
