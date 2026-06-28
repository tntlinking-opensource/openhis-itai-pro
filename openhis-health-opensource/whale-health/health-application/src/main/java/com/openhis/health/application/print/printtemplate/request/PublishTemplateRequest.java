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

package com.openhis.health.application.print.printtemplate.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 发布模板请求（IF-BAS-PRT-15）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "发布画布打印模板请求")
public class PublishTemplateRequest {

  /** 待发布的模板 busNo（须存在租户 draft）。 */
  @Schema(description = "模板 busNo", requiredMode = Schema.RequiredMode.REQUIRED,
      example = "PT20260101001")
  private String busNo;
}
