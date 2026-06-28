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

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 打印日志明细分页查询请求（IF-BAS-PRT-01）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "打印日志明细分页查询请求")
public class QueryPrintLogRequest extends AbstractHealthPageRequest {

  /** 模板 busNo 精确筛选；可空。 */
  @Schema(description = "模板 busNo 精确筛选", example = "PT20260101001")
  private String busNo;

  /** 打印结果：{@code SUCCESS} / {@code FAILED}；可空。 */
  @Schema(description = "打印结果：SUCCESS / FAILED", example = "SUCCESS")
  private String status;

  /** 创建日起（含）；可空。 */
  @Schema(description = "创建日起（含）", example = "2026-01-01")
  private LocalDate dateFrom;

  /** 创建日止（含）；可空。 */
  @Schema(description = "创建日止（含）", example = "2026-01-31")
  private LocalDate dateTo;
}
