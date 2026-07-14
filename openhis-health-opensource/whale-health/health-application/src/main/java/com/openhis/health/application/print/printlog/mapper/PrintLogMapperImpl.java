/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printlog.mapper;

import com.openhis.health.application.print.printlog.dto.PrintDailySummaryItemDto;
import com.openhis.health.application.print.printlog.dto.PrintEventLogItemDto;
import com.openhis.health.domain.share.enums.PrintLogStatus;
import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link PrintLogMapper} 实现：字段直拷 + 日汇总内存聚合。
 */
@Component
public class PrintLogMapperImpl implements PrintLogMapper {

  /**
   * 审计日志实体转明细分页行 DTO。
   *
   * @param log 日志行；null 时返回 null
   * @return 明细分页行
   */
  @Override
  public PrintEventLogItemDto ConverterPrintEventLogItemDto(PrtPrintEventLog log) {
    if (log == null) {
      return null;
    }
    PrintEventLogItemDto dto = new PrintEventLogItemDto();
    dto.setId(log.getId());
    dto.setTraceId(log.getTraceId());
    dto.setBusNo(log.getBusNo());
    dto.setBindingCode(log.getBindingCode());
    dto.setTemplateVersion(log.getTemplateVersion());
    dto.setStatus(log.getStatus());
    dto.setPrinterName(log.getPrinterName());
    dto.setCopies(log.getCopies());
    dto.setOperatorId(log.getOperatorId());
    dto.setOperatorName(log.getOperatorName());
    dto.setErrorCode(log.getErrorCode());
    dto.setErrorMessage(log.getErrorMessage());
    dto.setCreatedAt(log.getCreatedAt());
    return dto;
  }

  /**
   * 扫描到的日志明细按 day|busNo 聚合为日汇总行。
   *
   * @param logs 单次扫描结果
   * @param fallbackDay 无 createdAt 时的兜底日期
   * @return 汇总行列表，按日降序、busNo 升序
   */
  @Override
  public List<PrintDailySummaryItemDto> ConverterPrintDailySummaryList(
      List<PrtPrintEventLog> logs, LocalDate fallbackDay) {
    Map<String, PrintDailySummaryItemDto> aggregate = new HashMap<>();
    for (PrtPrintEventLog log : logs) {
      LocalDate day = fallbackDay;
      if (log.getCreatedAt() != null) {
        day = log.getCreatedAt().toLocalDate();
      }
      String key = day + "|" + log.getBusNo();
      PrintDailySummaryItemDto row = aggregate.get(key);
      if (row == null) {
        row = new PrintDailySummaryItemDto();
        row.setDay(day);
        row.setBusNo(log.getBusNo());
        row.setSuccessCount(0);
        row.setFailedCount(0);
        aggregate.put(key, row);
      }
      if (PrintLogStatus.SUCCESS.sameAs(log.getStatus())) {
        row.setSuccessCount(row.getSuccessCount() + 1);
      } else {
        row.setFailedCount(row.getFailedCount() + 1);
      }
    }
    List<PrintDailySummaryItemDto> result = new ArrayList<>(aggregate.values());
    result.sort(
        (a, b) -> {
          int dayCompare = b.getDay().compareTo(a.getDay());
          if (dayCompare != 0) {
            return dayCompare;
          }
          return a.getBusNo().compareTo(b.getBusNo());
        });
    return result;
  }
}
