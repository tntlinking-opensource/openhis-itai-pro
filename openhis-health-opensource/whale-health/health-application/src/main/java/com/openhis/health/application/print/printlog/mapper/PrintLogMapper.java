/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
