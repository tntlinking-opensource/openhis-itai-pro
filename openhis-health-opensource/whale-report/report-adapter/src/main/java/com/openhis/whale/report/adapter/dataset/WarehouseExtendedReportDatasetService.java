/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.dataset;
import com.openhis.whale.report.adapter.support.ReportDatasetTextSupport;
import com.openhis.whale.report.adapter.support.ReportDatasetParamSupport;

import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.report.contracts.constants.ReportDatasetLimits;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库房扩展 C 报表数据集（PH-05 / PH-09 / PH-10）。
 */
@Service
@RequiredArgsConstructor
public class WarehouseExtendedReportDatasetService {

    private final SupplyRequestQueryService supplyRequestQueryService;

    /**
     * 库存盘点 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildStocktake(Map<String, Object> params) {
        return buildFilteredDocuments(params, SupplyType::isStocktakeTypeCode);
    }
    /**
     * 库房调拨 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildTransfer(Map<String, Object> params) {
        return buildFilteredDocuments(params, this::isTransferType);
    }
    /**
     * 出入库单明细 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildDocumentDetail(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);

        SupplyRequestDocumentListQuery query = new SupplyRequestDocumentListQuery();
        query.setPageNo(1);
        query.setPageSize(ReportDatasetLimits.MAX_TABULAR_ROWS);
        query.setStartDate(ReportDatasetParamSupport.startOf(dateFrom));
        query.setEndDate(ReportDatasetParamSupport.endOf(dateTo));

        List<String> busNos = supplyRequestQueryService.listDistinctBusNoPage(query);
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (busNos != null && !busNos.isEmpty()) {
            SupplyRequestDocumentListQuery lineFilter = new SupplyRequestDocumentListQuery();
            lineFilter.setStartDate(query.getStartDate());
            lineFilter.setEndDate(query.getEndDate());
            var lines = supplyRequestQueryService.listByBusNosWithFilter(lineFilter, busNos);
            for (var line : lines) {
                if (line == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("busNo", StringUtils.defaultIfBlank(line.getBusNo(), "—"));
                row.put("typeEnum", StringUtils.defaultIfBlank(line.getTypeEnum(), "—"));
                row.put("statusEnum", StringUtils.defaultIfBlank(line.getStatusEnum(), "—"));
                row.put("itemQuantity", formatQty(line.getItemQuantity()));
                row.put("totalPrice", formatMoney(line.getTotalPrice()));
                row.put("applyTime", ReportDatasetTextSupport.formatDateTimeOrDash(line.getApplyTime()));
                rows.add(row);
                if (line.getTotalPrice() != null) {
                    totalAmount = totalAmount.add(line.getTotalPrice());
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("lineCount", rows.size());
        summary.put("totalAmount", formatMoney(totalAmount));
        return root(rows, summary);
    }

    private Map<String, Object> buildFilteredDocuments(
            Map<String, Object> params,
            java.util.function.Predicate<String> typeFilter) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);

        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setPageNo(1);
        query.setPageSize(ReportDatasetLimits.MAX_TABULAR_ROWS);
        query.setApplyTimeStart(ReportDatasetParamSupport.startOf(dateFrom));
        query.setApplyTimeEnd(ReportDatasetParamSupport.endOf(dateTo));

        Page<WarehouseDocumentDetailQueryModel> page =
                supplyRequestQueryService.pageWarehouseDocumentDetail(query);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int documentCount = 0;
        for (WarehouseDocumentDetailQueryModel doc : page.getContent()) {
            if (doc == null || !typeFilter.test(doc.getTypeEnum())) {
                // 跳过非目标单据类型
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("busNo", StringUtils.defaultIfBlank(doc.getBusNo(), "—"));
            row.put("typeEnum", StringUtils.defaultIfBlank(doc.getTypeEnum(), "—"));
            row.put("statusEnum", StringUtils.defaultIfBlank(doc.getStatusEnum(), "—"));
            row.put("itemQuantity", formatQty(doc.getItemQuantity()));
            row.put("totalPrice", formatMoney(doc.getTotalPrice()));
            row.put("applyTime", ReportDatasetTextSupport.formatDateTimeOrDash(doc.getApplyTime()));
            rows.add(row);
            documentCount += 1;
            if (doc.getTotalPrice() != null) {
                totalAmount = totalAmount.add(doc.getTotalPrice());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("documentCount", documentCount);
        summary.put("totalAmount", formatMoney(totalAmount));
        return root(rows, summary);
    }

    private boolean isTransferType(String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            return false;
        }
        return typeEnum.contains("TRANSFER");
    }

    private static Map<String, Object> root(List<Map<String, Object>> rows, Map<String, Object> summary) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    private static String formatMoney(BigDecimal value) {
        return ReportDatasetTextSupport.zeroIfNull(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String formatQty(BigDecimal value) {
        return ReportDatasetTextSupport.zeroIfNull(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
