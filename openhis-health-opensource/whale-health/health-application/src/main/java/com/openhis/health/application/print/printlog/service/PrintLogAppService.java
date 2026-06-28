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

package com.openhis.health.application.print.printlog.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.print.printlog.dto.PrintDailySummaryItemDto;
import com.openhis.health.application.print.printlog.dto.PrintEventLogItemDto;
import com.openhis.health.application.print.printlog.mapper.PrintLogMapper;
import com.openhis.health.application.print.printlog.request.QueryPrintDailySummaryRequest;
import com.openhis.health.application.print.printlog.request.QueryPrintLogRequest;
import com.openhis.health.application.print.printlog.response.QueryPrintLogPageResponse;
import com.openhis.health.module.common.validator.PrintOperatorResolver;
import com.openhis.health.domain.share.constants.PrintConstants;
import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import com.openhis.health.domain.print.printlog.queries.PrtPrintEventLogPageQuery;
import com.openhis.health.domain.print.printlog.service.PrtPrintEventLogQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 打印审计日志只读应用服务（AS-BAS-PRT，IF-BAS-PRT-01～02）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>明细分页与日汇总只读查询；</li>
 *   <li>只读经 {@link PrtPrintEventLogQueryService}，禁止直注 Repository；</li>
 *   <li>出参经 {@link PrintLogMapper} 装配。</li>
 * </ul>
 *
 * <p>由 AppServiceControllerRegistrar 暴露 REST，本类不含 Controller。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintLogAppService {

  /** 打印日志读侧：明细分页与日汇总扫描。 */
  private final PrtPrintEventLogQueryService prtPrintEventLogQueryService;

  /** 会话租户解析。 */
  private final PrintOperatorResolver printOperatorResolver;

  /** 日志实体 → DTO 与日汇总聚合。 */
  private final PrintLogMapper printLogMapper;

  /**
   * 明细分页查询（IF-BAS-PRT-01）。
   *
   * @param request 筛选与分页；可 null
   * @return 分页明细
   */
  public QueryPrintLogPageResponse queryPage(QueryPrintLogRequest request) {
    // Gate：须已选租户，日志按租户隔离。
    QueryPrintLogRequest safeRequest = normalizeLogQueryRequest(request);
    Long tenantId = printOperatorResolver.requireTenantId();
    // Pre：归一化筛选条件与分页窗口。
    String busNo = trimToNull(safeRequest.getBusNo());
    String status = normalizeLogStatus(safeRequest.getStatus());
    LocalDate dateFrom = safeRequest.getDateFrom();
    LocalDate dateTo = safeRequest.getDateTo();
    int pageNo = resolvePageNo(safeRequest.getPageNo());
    int pageSize = resolvePageSize(safeRequest.getPageSize());
    PageRequest pageable =
        PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    // Cmd：经 QueryService 下推 DB 分页。
    PrtPrintEventLogPageQuery pageQuery = new PrtPrintEventLogPageQuery();
    pageQuery.setTenantId(tenantId);
    pageQuery.setBusNo(busNo);
    pageQuery.setStatus(status);
    pageQuery.setDateFrom(dateFrom);
    pageQuery.setDateTo(dateTo);
    Page<PrtPrintEventLog> page =
        prtPrintEventLogQueryService.pageByFilter(pageQuery, pageable);
    // Asm：Mapper 映射为 DTO 并组装 PagedResult。
    List<PrintEventLogItemDto> rows = new ArrayList<>();
    for (PrtPrintEventLog log : page.getContent()) {
      rows.add(printLogMapper.ConverterPrintEventLogItemDto(log));
    }
    return PagedResultBuilder.build(new QueryPrintLogPageResponse(), rows, page);
  }

  /**
   * 日汇总查询（IF-BAS-PRT-02）：按日+busNo 统计成功/失败次数。
   *
   * @param request 日期范围与 busNo；dateFrom/dateTo 可空（默认近 7 日）
   * @return 汇总行列表，按日降序、busNo 升序
   */
  public List<PrintDailySummaryItemDto> queryDailySummary(QueryPrintDailySummaryRequest request) {
    QueryPrintDailySummaryRequest safeRequest = normalizeSummaryRequest(request);
    // Gate：须已选租户。
    Long tenantId = printOperatorResolver.requireTenantId();
    LocalDate today = LocalDate.now();
    LocalDate dateFrom = safeRequest.getDateFrom();
    if (dateFrom == null) {
      // 未传起始日时默认回溯固定天数。
      dateFrom = today.minusDays(PrintConstants.DAILY_SUMMARY_DEFAULT_LOOKBACK_DAYS);
    }
    LocalDate dateTo = safeRequest.getDateTo();
    if (dateTo == null) {
      dateTo = today;
    }
    String busNo = trimToNull(safeRequest.getBusNo());
    // Pre：单次扫描上限内拉取明细，内存按 day|busNo 聚合。
    PageRequest pageable =
        PageRequest.of(
            0,
            PrintConstants.DAILY_SUMMARY_SCAN_PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "createdAt"));
    PrtPrintEventLogPageQuery summaryQuery = new PrtPrintEventLogPageQuery();
    summaryQuery.setTenantId(tenantId);
    summaryQuery.setBusNo(busNo);
    summaryQuery.setDateFrom(dateFrom);
    summaryQuery.setDateTo(dateTo);
    Page<PrtPrintEventLog> page =
        prtPrintEventLogQueryService.pageForDailySummary(summaryQuery, pageable);
    // Asm：Mapper 聚合后返回汇总行。
    return printLogMapper.ConverterPrintDailySummaryList(page.getContent(), dateFrom);
  }

  private QueryPrintLogRequest normalizeLogQueryRequest(QueryPrintLogRequest request) {
    if (request == null) {
      return new QueryPrintLogRequest();
    }
    return request;
  }

  private QueryPrintDailySummaryRequest normalizeSummaryRequest(
      QueryPrintDailySummaryRequest request) {
    if (request == null) {
      return new QueryPrintDailySummaryRequest();
    }
    return request;
  }

  private String normalizeLogStatus(String status) {
    String resolved = trimToNull(status);
    if (resolved == null) {
      return null;
    }
    return resolved.toUpperCase(Locale.ROOT);
  }

  private int resolvePageNo(Integer pageNo) {
    if (pageNo == null || pageNo < 1) {
      return 1;
    }
    return pageNo;
  }

  private int resolvePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
    }
    return Math.min(pageSize, PrintConstants.PAGE_SIZE_MAX);
  }
}
