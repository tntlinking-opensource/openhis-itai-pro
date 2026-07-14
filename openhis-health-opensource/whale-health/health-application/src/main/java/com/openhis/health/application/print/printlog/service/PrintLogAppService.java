/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printlog.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.print.printlog.dto.PrintDailySummaryItemDto;
import com.openhis.health.application.print.printlog.dto.PrintEventLogItemDto;
import com.openhis.health.application.print.printlog.mapper.PrintLogMapper;
import com.openhis.health.application.print.printlog.request.QueryPrintDailySummaryRequest;
import com.openhis.health.application.print.printlog.request.QueryPrintLogRequest;
import com.openhis.health.application.print.printlog.response.QueryPrintLogPageResponse;
import com.openhis.health.application.shared.validator.PrintOperatorResolver;
import com.openhis.health.domain.share.constants.PrintConstants;
import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import com.openhis.health.domain.print.printlog.queries.PrtPrintEventLogPageQuery;
import com.openhis.health.domain.print.printlog.service.PrtPrintEventLogQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
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
    QueryPrintLogRequest safeRequest = request;
    if (safeRequest == null) {
      safeRequest = new QueryPrintLogRequest();
    }
    Long tenantId = printOperatorResolver.requireTenantId();
    // Pre：归一化筛选条件与分页窗口。
    String busNo = trimToNull(safeRequest.getBusNo());
    String status = trimToNull(safeRequest.getStatus());
    if (status != null) {
      status = status.toUpperCase(Locale.ROOT);
    }
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
    QueryPrintDailySummaryRequest safeRequest = request;
    if (safeRequest == null) {
      safeRequest = new QueryPrintDailySummaryRequest();
    }
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

  /**
   * 对外 pageNo 归一：null 或 &lt;1 则 1。
   *
   * @param pageNo 请求页码
   * @return 1-based 页码
   */
  private int resolvePageNo(Integer pageNo) {
    if (pageNo == null || pageNo < 1) {
      return 1;
    }
    return pageNo;
  }

  /**
   * 对外 pageSize 归一：null 或 &lt;1 用默认，上限 {@link PrintConstants#PAGE_SIZE_MAX}。
   *
   * @param pageSize 请求页大小
   * @return 合法页大小
   */
  private int resolvePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
    }
    return Math.min(pageSize, PrintConstants.PAGE_SIZE_MAX);
  }
}
