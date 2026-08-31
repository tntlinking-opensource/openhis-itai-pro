/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printlog.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.print.printlog.dto.PrintDailySummaryItemDto;
import com.openhis.health.application.print.printlog.dto.PrintEventLogItemDto;
import com.openhis.health.application.print.printlog.mapper.PrintLogMapper;
import com.openhis.health.application.print.printlog.request.QueryPrintDailySummaryRequest;
import com.openhis.health.application.print.printlog.request.QueryPrintedKeysRequest;
import com.openhis.health.application.print.printlog.request.QueryPrintLogRequest;
import com.openhis.health.application.print.printlog.response.QueryPrintedKeysResponse;
import com.openhis.health.application.print.printlog.response.QueryPrintLogPageResponse;
import com.openhis.health.application.shared.validator.PrintOperatorResolver;
import com.openhis.health.domain.share.constants.PrintConstants;
import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import com.openhis.health.domain.print.printlog.queries.PrtPrintEventLogPageQuery;
import com.openhis.health.domain.print.printlog.service.PrtPrintEventLogQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.exception.ValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

  /** 解析 biz_ref_json 中的主键数组。 */
  private final ObjectMapper objectMapper;

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
   * 按业务主键批量回查已打印（PRT-GLOBAL-001 §6 · 摆药单试点）。
   *
   * <p>扫描场景 SUCCESS 日志，解析 {@code biz_ref_json} 中数组字段与入参 keys 求交。</p>
   *
   * @param request 场景码、主键列表、可选查询日
   * @return 已打印主键及最近打印时刻
   */
  public QueryPrintedKeysResponse queryPrintedKeys(QueryPrintedKeysRequest request) {
    if (request == null) {
      throw new ValidationException("请求不能为空");
    }
    // Gate：租户 + 场景码 + keys
    Long tenantId = printOperatorResolver.requireTenantId();
    String scenarioCode = trimToNull(request.getScenarioCode());
    if (scenarioCode == null) {
      throw new ValidationException("scenarioCode 不能为空");
    }
    List<String> rawKeys = request.getKeys();
    if (rawKeys == null || rawKeys.isEmpty()) {
      throw new ValidationException("keys 不能为空");
    }
    // Pre：去空白去重，截断单批上限
    LinkedHashSet<String> wanted = new LinkedHashSet<>();
    for (String raw : rawKeys) {
      String key = trimToNull(raw);
      if (key == null) {
        continue;
      }
      wanted.add(key);
      if (wanted.size() >= PrintConstants.QUERY_PRINTED_KEYS_MAX) {
        break;
      }
    }
    QueryPrintedKeysResponse response = new QueryPrintedKeysResponse();
    if (wanted.isEmpty()) {
      return response;
    }
    String keyField = trimToNull(request.getKeyField());
    if (keyField == null) {
      keyField = PrintConstants.BIZ_REF_KEY_FIELD_LIST_ROW_KEYS;
    }
    LocalDate queryDay = parseQueryDateOrNull(request.getQueryDate());
    LocalDate bizDateFrom = parseQueryDateOrNull(request.getBizDateFrom());
    LocalDate bizDateTo = parseQueryDateOrNull(request.getBizDateTo());
    // Pre：created_at 一律按回溯窗扫描；业务日只在 bizRef 层过滤（避免业务日与打印日不一致漏查）
    LocalDate today = LocalDate.now();
    LocalDate scanFrom = today.minusDays(PrintConstants.QUERY_PRINTED_DEFAULT_LOOKBACK_DAYS);
    LocalDate scanTo = today;
    // Cmd：读侧扫描 SUCCESS
    List<PrtPrintEventLog> logs = prtPrintEventLogQueryService.listSuccessByBindingCode(
        tenantId, scenarioCode, scanFrom, scanTo);
    // Asm：解析 bizRef 数组/标量，求交并保留最近打印时刻
    Map<String, LocalDateTime> latestAt = new LinkedHashMap<>();
    DateTimeFormatter displayFmt =
        DateTimeFormatter.ofPattern(GeneralSharedConstants.DATE_TIME_PATTERN);
    for (PrtPrintEventLog logRow : logs) {
      if (logRow == null) {
        continue;
      }
      Set<String> logKeys =
          extractBizRefKeys(logRow.getBizRefJson(), keyField, queryDay, bizDateFrom, bizDateTo);
      if (logKeys.isEmpty()) {
        continue;
      }
      LocalDateTime printedAt = logRow.getCreatedAt();
      for (String key : wanted) {
        if (!logKeys.contains(key)) {
          continue;
        }
        LocalDateTime prev = latestAt.get(key);
        if (prev == null || (printedAt != null && printedAt.isAfter(prev))) {
          latestAt.put(key, printedAt);
        }
      }
    }
    List<String> printedKeys = new ArrayList<>();
    Map<String, String> lastPrintedAtByKey = new LinkedHashMap<>();
    for (String key : wanted) {
      if (!latestAt.containsKey(key)) {
        continue;
      }
      printedKeys.add(key);
      LocalDateTime at = latestAt.get(key);
      if (at != null) {
        lastPrintedAtByKey.put(key, at.format(displayFmt));
      }
    }
    response.setPrintedKeys(printedKeys);
    response.setLastPrintedAtByKey(lastPrintedAtByKey);
    return response;
  }

  /**
   * 解析 YYYY-MM-DD；非法则 null。
   *
   * @param raw 查询日字符串
   * @return LocalDate 或 null
   */
  private LocalDate parseQueryDateOrNull(String raw) {
    String day = trimToNull(raw);
    if (day == null) {
      return null;
    }
    try {
      return LocalDate.parse(day);
    } catch (DateTimeParseException ex) {
      throw new ValidationException("queryDate 须为 YYYY-MM-DD");
    }
  }

  /**
   * 从 biz_ref_json 提取主键集合；可选按 queryDate / dateFrom·dateTo 二次过滤。
   *
   * @param bizRefJson  日志业务引用 JSON
   * @param keyField    主键字段名（数组或标量）
   * @param queryDay    业务日；非空且 bizRef 含 queryDate 时须相等才计入
   * @param bizDateFrom 区间起；与 bizDateTo 同时非空时要求 bizRef.dateFrom/dateTo 一致
   * @param bizDateTo   区间止
   * @return 主键集合
   */
  private Set<String> extractBizRefKeys(
      String bizRefJson,
      String keyField,
      LocalDate queryDay,
      LocalDate bizDateFrom,
      LocalDate bizDateTo) {
    String json = trimToNull(bizRefJson);
    if (json == null) {
      return Set.of();
    }
    try {
      JsonNode root = objectMapper.readTree(json);
      if (root == null || !root.isObject()) {
        return Set.of();
      }
      // Gate：业务日 / 区间过滤，避免跨日误命中
      if (queryDay != null && root.hasNonNull("queryDate")) {
        String refDay = trimToNull(root.get("queryDate").asText());
        if (refDay != null && !queryDay.toString().equals(refDay)) {
          return Set.of();
        }
      }
      if (bizDateFrom != null && bizDateTo != null) {
        String refFrom = trimToNull(root.path("dateFrom").asText(null));
        String refTo = trimToNull(root.path("dateTo").asText(null));
        if (refFrom == null
            || refTo == null
            || !bizDateFrom.toString().equals(refFrom)
            || !bizDateTo.toString().equals(refTo)) {
          return Set.of();
        }
      }
      // Asm：数组优先；否则读标量；再试单复数同义字段
      Set<String> keys = new HashSet<>();
      collectKeysFromField(root, keyField, keys);
      if (keys.isEmpty()) {
        String alias = resolveKeyFieldAlias(keyField);
        if (alias != null) {
          collectKeysFromField(root, alias, keys);
        }
      }
      return keys;
    } catch (Exception ex) {
      // 坏 JSON 跳过该行，不阻断整批回查
      log.warn("解析打印审计 biz_ref_json 失败，已跳过该行", ex);
      return Set.of();
    }
  }

  /**
   * 从对象节点收集指定字段的主键（数组各元素或单个标量）。
   *
   * @param root  bizRef 根对象
   * @param field 字段名
   * @param keys  输出集合
   */
  private void collectKeysFromField(JsonNode root, String field, Set<String> keys) {
    JsonNode node = root.get(field);
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        if (item == null || item.isNull()) {
          continue;
        }
        String key = trimToNull(item.asText());
        if (key != null) {
          keys.add(key);
        }
      }
      return;
    }
    String key = trimToNull(node.asText());
    if (key != null) {
      keys.add(key);
    }
  }

  /**
   * 单复数同义字段：encounterIds↔encounterId、serviceRequestIds↔serviceRequestId、summaryNos↔summaryNo。
   *
   * @param keyField 请求字段名
   * @return 同义字段；无则 null
   */
  private String resolveKeyFieldAlias(String keyField) {
    if ("encounterIds".equals(keyField)) {
      return "encounterId";
    }
    if ("encounterId".equals(keyField)) {
      return "encounterIds";
    }
    if ("serviceRequestIds".equals(keyField)) {
      return "serviceRequestId";
    }
    if ("serviceRequestId".equals(keyField)) {
      return "serviceRequestIds";
    }
    if ("summaryNos".equals(keyField)) {
      return "summaryNo";
    }
    if ("summaryNo".equals(keyField)) {
      return "summaryNos";
    }
    if ("listRowKeys".equals(keyField)) {
      return "listRowKey";
    }
    if ("listRowKey".equals(keyField)) {
      return "listRowKeys";
    }
    return null;
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
