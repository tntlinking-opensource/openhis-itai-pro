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
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 绑定配置加载响应（IF-BAS-PRT-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "业务场景模板绑定配置")
public class BindingConfigDto {

  /** 平台出厂场景定义 + 租户 override 合并后的绑定行。 */
  @Schema(description = "场景绑定行列表")
  private List<BindingItemDto> bindings = new ArrayList<>();

  /** 租户可选模板候选（draft / published）。 */
  @Schema(description = "可选模板候选列表")
  private List<TemplateCandidateDto> candidates = new ArrayList<>();
}
