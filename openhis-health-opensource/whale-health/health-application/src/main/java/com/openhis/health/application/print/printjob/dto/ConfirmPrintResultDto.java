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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印确认结果 DTO（IF-BAS-PRT-32）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印确认结果")
public class ConfirmPrintResultDto {

  /** 确认是否受理；当前恒为 true（失败由 HTTP 层异常表达）。 */
  @Schema(description = "是否受理", example = "true")
  private Boolean success = Boolean.TRUE;
}
