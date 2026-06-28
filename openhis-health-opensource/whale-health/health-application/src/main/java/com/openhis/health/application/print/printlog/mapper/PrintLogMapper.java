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

package com.openhis.health.application.print.printlog.mapper;

import com.openhis.health.application.print.printlog.dto.PrintDailySummaryItemDto;
import com.openhis.health.application.print.printlog.dto.PrintEventLogItemDto;
import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import java.time.LocalDate;
import java.util.List;

/**
 * 打印审计日志应用层装配：实体 → 明细分页 DTO / 日汇总 DTO。
 */
public interface PrintLogMapper {

  /**
   * 审计日志实体转明细分页行 DTO。
   *
   * @param log 日志行；null 时返回 null
   * @return 明细分页行
   */
  PrintEventLogItemDto ConverterPrintEventLogItemDto(PrtPrintEventLog log);

  /**
   * 扫描到的日志明细按 day|busNo 聚合为日汇总行。
   *
   * @param logs 单次扫描结果
   * @param fallbackDay 无 createdAt 时的兜底日期
   * @return 汇总行列表，按日降序、busNo 升序
   */
  List<PrintDailySummaryItemDto> ConverterPrintDailySummaryList(
      List<PrtPrintEventLog> logs, LocalDate fallbackDay);
}
