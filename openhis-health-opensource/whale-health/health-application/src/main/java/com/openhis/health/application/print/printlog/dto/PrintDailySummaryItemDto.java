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

package com.openhis.health.application.print.printlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印审计日汇总行 DTO（IF-BAS-PRT-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印审计日汇总行")
public class PrintDailySummaryItemDto {

  /** 汇总日期（按 createdAt 取 local date）。 */
  @Schema(description = "汇总日期", example = "2026-01-15")
  private LocalDate day;

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 当日成功打印次数。 */
  @Schema(description = "成功次数", example = "42")
  private Integer successCount;

  /** 当日失败打印次数。 */
  @Schema(description = "失败次数", example = "3")
  private Integer failedCount;
}
