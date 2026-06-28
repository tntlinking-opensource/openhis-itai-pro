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

package com.openhis.health.application.print.printlog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印日志日汇总查询请求（IF-BAS-PRT-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印日志日汇总查询请求")
public class QueryPrintDailySummaryRequest {

  /** 汇总起始日（含）；空则默认近 7 日。 */
  @Schema(description = "汇总起始日（含）", example = "2026-01-01")
  private LocalDate dateFrom;

  /** 汇总结束日（含）；空则默认当天。 */
  @Schema(description = "汇总结束日（含）", example = "2026-01-31")
  private LocalDate dateTo;

  /** 模板 busNo 精确筛选；可空表示全部。 */
  @Schema(description = "模板 busNo 精确筛选", example = "PT20260101001")
  private String busNo;
}
