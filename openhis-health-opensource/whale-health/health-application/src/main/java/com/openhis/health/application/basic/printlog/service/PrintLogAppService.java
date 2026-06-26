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

package com.openhis.health.application.basic.printlog.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.basic.print.support.PrintOperatorResolver;
import com.openhis.health.application.basic.printlog.dto.PrintDailySummaryItemDto;
import com.openhis.health.application.basic.printlog.dto.PrintEventLogItemDto;
import com.openhis.health.application.basic.printlog.request.QueryPrintDailySummaryRequest;
import com.openhis.health.application.basic.printlog.request.QueryPrintLogRequest;
import com.openhis.health.application.basic.printlog.response.QueryPrintLogPageResponse;
import com.openhis.health.domain.custom.printlog.constants.PrintLogStatus;
import com.openhis.health.domain.custom.printlog.entity.PrtPrintEventLog;
import com.openhis.health.domain.custom.printlog.repository.PrtPrintEventLogRepository;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * 打印审计日志只读应用服务（IF-BAS-PRT-01～02）。
 */
@Service
@RequiredArgsConstructor
public class PrintLogAppService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int DAILY_SUMMARY_SCAN_PAGE_SIZE = 10000;

  private final PrtPrintEventLogRepository prtPrintEventLogRepository;
  private final PrintOperatorResolver printOperatorResolver;

  /**
   * 明细分页查询（IF-BAS-PRT-01）。
   *
   * @param request 筛选与分页
   * @return 分页明细
   */
  public QueryPrintLogPageResponse queryPage(QueryPrintLogRequest request) {
    QueryPrintLogRequest safeRequest = request == null ? new QueryPrintLogRequest() : request;
    Long tenantId = printOperatorResolver.requireTenantId();
    String busNo = trimToNull(safeRequest.getBusNo());
    String status = trimToNull(safeRequest.getStatus());
    if (status != null) {
      status = status.toUpperCase(Locale.ROOT);
    }
    LocalDate dateFrom = safeRequest.getDateFrom();
    LocalDate dateTo = safeRequest.getDateTo();
    Specification<PrtPrintEventLog> spec =
        buildSpecification(tenantId, busNo, status, dateFrom, dateTo);
    int pageNo = resolvePageNo(safeRequest.getPageNo());
    int pageSize = resolvePageSize(safeRequest.getPageSize());
    PageRequest pageable =
        PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<PrtPrintEventLog> page = prtPrintEventLogRepository.findAll(spec, pageable);
    List<PrintEventLogItemDto> rows = new ArrayList<>();
    for (PrtPrintEventLog log : page.getContent()) {
      rows.add(toItemDto(log));
    }
    return PagedResultBuilder.build(new QueryPrintLogPageResponse(), rows, page);
  }

  /**
   * 日汇总查询（IF-BAS-PRT-02）。
   *
   * @param request 日期范围与 busNo
   * @return 按日+busNo 汇总行
   */
  public List<PrintDailySummaryItemDto> queryDailySummary(QueryPrintDailySummaryRequest request) {
    QueryPrintDailySummaryRequest safeRequest =
        request == null ? new QueryPrintDailySummaryRequest() : request;
    Long tenantId = printOperatorResolver.requireTenantId();
    LocalDate today = LocalDate.now();
    LocalDate dateFrom = safeRequest.getDateFrom() != null ? safeRequest.getDateFrom() : today.minusDays(7);
    LocalDate dateTo = safeRequest.getDateTo() != null ? safeRequest.getDateTo() : today;
    String busNo = trimToNull(safeRequest.getBusNo());
    Specification<PrtPrintEventLog> spec =
        buildSpecification(tenantId, busNo, null, dateFrom, dateTo);
    PageRequest pageable =
        PageRequest.of(0, DAILY_SUMMARY_SCAN_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<PrtPrintEventLog> page = prtPrintEventLogRepository.findAll(spec, pageable);
    Map<String, PrintDailySummaryItemDto> aggregate = new HashMap<>();
    for (PrtPrintEventLog log : page.getContent()) {
      LocalDate day =
          log.getCreatedAt() != null ? log.getCreatedAt().toLocalDate() : dateFrom;
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
      if (PrintLogStatus.SUCCESS.equals(log.getStatus())) {
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

  private Specification<PrtPrintEventLog> buildSpecification(
      Long tenantId,
      String busNo,
      String status,
      LocalDate dateFrom,
      LocalDate dateTo) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      predicates.add(cb.equal(root.get("isDeleted"), Boolean.FALSE));
      if (busNo != null) {
        predicates.add(cb.equal(root.get("busNo"), busNo));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (dateFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
      }
      if (dateTo != null) {
        predicates.add(cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private PrintEventLogItemDto toItemDto(PrtPrintEventLog log) {
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

  private int resolvePageNo(Integer pageNo) {
    if (pageNo == null || pageNo < 1) {
      return 1;
    }
    return pageNo;
  }

  private int resolvePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, 1000);
  }
}
