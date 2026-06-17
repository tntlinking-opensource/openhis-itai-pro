/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.tabular.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;
import com.openhis.health.application.report.support.ReportDatasetParamSupport;

import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临床/静配 C 报表数据集（IP-09 / LAB-05 / PIVAS-02）。
 */
@Service
@RequiredArgsConstructor
public class ClinicalCClassReportDatasetService {

    private static final int MAX_ROWS = 500;

    private final ProcedureQueryService procedureQueryService;    /**
     * 医嘱执行批次 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildOrderExecuteBatch(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);

        OrderExecutionSheetPageQuery query = new OrderExecutionSheetPageQuery();
        query.setPageNo(1);
        query.setPageSize(MAX_ROWS);
        query.setStartTime(ReportDatasetParamSupport.startOf(dateFrom));
        query.setEndTime(ReportDatasetParamSupport.endOf(dateTo));

        Page<Procedure> page = procedureQueryService.pageProceduresForOrderExecutionSheet(query);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Procedure procedure : page.getContent()) {
            if (procedure == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("procedureId", ReportDatasetTextSupport.formatLongOrDash(procedure.getId()));
            row.put("encounterId", ReportDatasetTextSupport.formatLongOrDash(procedure.getEncounterId()));
            row.put("categoryEnum", StringUtils.defaultIfBlank(procedure.getCategoryEnum(), "—"));
            row.put("statusEnum", StringUtils.defaultIfBlank(procedure.getStatusEnum(), "—"));
            row.put("procedureCode", StringUtils.defaultIfBlank(procedure.getProcedureCode(), "—"));
            row.put("occurrenceTime", ReportDatasetTextSupport.formatObjectOrDash(procedure.getOccurrenceTime()));
            rows.add(row);
        }
        if (rows.isEmpty()) {
            rows.add(executeRow("—", "—", "—", "—", "—", "—"));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("executeCount", page.getNumberOfElements());
        return root(rows, summary);
    }    /**
     * 检验工作清单 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildLabWorklist(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(worklistRow("样本接收", "待 LIS 适配器接入", "0"));
        rows.add(worklistRow("上机检验", "待 LIS 适配器接入", "0"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("hint", "外系统 LIS 工作清单待院点 Adapter 接线");
        return root(rows, summary);
    }    /**
     * 静配批次汇总 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildPivasBatchSummary(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(pivasRow("—", "0", "0"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("batchCount", 0);
        summary.put("hint", "静配模块待院点启用后接线");
        return root(rows, summary);
    }

    private static Map<String, Object> executeRow(
            String procedureId, String encounterId, String category, String status, String code, String time) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("procedureId", procedureId);
        row.put("encounterId", encounterId);
        row.put("categoryEnum", category);
        row.put("statusEnum", status);
        row.put("procedureCode", code);
        row.put("occurrenceTime", time);
        return row;
    }

    private static Map<String, Object> worklistRow(String step, String sampleHint, String count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("workStep", step);
        row.put("sampleHint", sampleHint);
        row.put("itemCount", count);
        return row;
    }

    private static Map<String, Object> pivasRow(String batchNo, String bagCount, String volume) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchNo", batchNo);
        row.put("bagCount", bagCount);
        row.put("totalVolume", volume);
        return row;
    }

    private static Map<String, Object> root(List<Map<String, Object>> rows, Map<String, Object> summary) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }
}
