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

  /** 候选行状态：{@code draft} 或 {@code published}。 */
  @Schema(description = "模板状态：draft / published", example = "published")
  private String status;
}
