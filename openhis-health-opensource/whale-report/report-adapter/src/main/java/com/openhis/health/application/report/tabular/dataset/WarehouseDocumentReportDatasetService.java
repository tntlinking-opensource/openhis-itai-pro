/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.tabular.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库/入库单 C 类报表数据集（PH-02）。
 */
@Service
@RequiredArgsConstructor
public class WarehouseDocumentReportDatasetService {

    private static final int MAX_ROWS = 500;

    private final SupplyRequestQueryService supplyRequestQueryService;    /**
     * 组装 C 类报表数据集 root Map（rows + summary）。
     *
     * @param params 查询参数（如 dateFrom、dateTo、deptId 等）
     * @return 含 rows、summary 的 root Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        LocalDate dateFrom = resolveDate(params, "dateFrom", "date_from");
        LocalDate dateTo = resolveDate(params, "dateTo", "date_to");
        if (dateFrom == null) {
            dateFrom = LocalDate.now();
        }
        if (dateTo == null) {
            dateTo = dateFrom;
        }
        if (dateTo.isBefore(dateFrom)) {
            dateTo = dateFrom;
        }

        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setPageNo(1);
        query.setPageSize(MAX_ROWS);
        query.setApplyTimeStart(dateFrom.atStartOfDay());
        query.setApplyTimeEnd(dateTo.atTime(LocalTime.MAX));

        Page<WarehouseDocumentDetailQueryModel> page =
                supplyRequestQueryService.pageWarehouseDocumentDetail(query);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (WarehouseDocumentDetailQueryModel doc : page.getContent()) {
            if (doc == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("busNo", StringUtils.defaultIfBlank(doc.getBusNo(), "—"));
            row.put("typeEnum", StringUtils.defaultIfBlank(doc.getTypeEnum(), "—"));
            row.put("statusEnum", StringUtils.defaultIfBlank(doc.getStatusEnum(), "—"));
            row.put("purposeLocationId", doc.getPurposeLocationId() != null
                    ? String.valueOf(doc.getPurposeLocationId())
                    : "—");
            row.put("itemQuantity", formatQty(doc.getItemQuantity()));
            row.put("totalPrice", formatMoney(doc.getTotalPrice()));
            row.put("unit", "元");
            if (doc.getApplyTime() != null) {
                row.put("applyTime", doc.getApplyTime().toString());
            } else {
                row.put("applyTime", "—");
            }
            rows.add(row);
            if (doc.getTotalPrice() != null) {
                totalAmount = totalAmount.add(doc.getTotalPrice());
            }
        }
        if (rows.isEmpty()) {
            rows.add(stubRow());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", formatDateRange(dateFrom, dateTo));
        summary.put("documentCount", page.getNumberOfElements());
        summary.put("totalAmount", formatMoney(totalAmount));
        if (page.getTotalElements() > MAX_ROWS) {
            summary.put("truncated", true);
            summary.put("truncatedHint", "仅展示前 " + MAX_ROWS + " 条");
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    /**
     * 按供应单据类型（{@code wkf_supply_request.type_enum}）查询 C 类报表。
     */
    public Map<String, Object> buildBySupplyType(Map<String, Object> params, String supplyTypeCode) {
        LocalDate dateFrom = resolveDate(params, "dateFrom", "date_from");
        LocalDate dateTo = resolveDate(params, "dateTo", "date_to");
        if (dateFrom == null) {
            dateFrom = LocalDate.now();
        }
        if (dateTo == null) {
            dateTo = dateFrom;
        }
        if (dateTo.isBefore(dateFrom)) {
            dateTo = dateFrom;
        }

        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setPageNo(1);
        query.setPageSize(MAX_ROWS);
        query.setApplyTimeStart(dateFrom.atStartOfDay());
        query.setApplyTimeEnd(dateTo.atTime(LocalTime.MAX));
        if (StringUtils.isNotBlank(supplyTypeCode)) {
            query.setTypeEnum(supplyTypeCode.trim());
        }

        Page<WarehouseDocumentDetailQueryModel> page =
                supplyRequestQueryService.pageWarehouseDocumentDetail(query);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (WarehouseDocumentDetailQueryModel doc : page.getContent()) {
            if (doc == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("busNo", StringUtils.defaultIfBlank(doc.getBusNo(), "—"));
            row.put("typeEnum", StringUtils.defaultIfBlank(doc.getTypeEnum(), "—"));
            row.put("statusEnum", StringUtils.defaultIfBlank(doc.getStatusEnum(), "—"));
            row.put("purposeLocationId", doc.getPurposeLocationId() != null
                    ? String.valueOf(doc.getPurposeLocationId())
                    : "—");
            row.put("itemQuantity", formatQty(doc.getItemQuantity()));
            row.put("totalPrice", formatMoney(doc.getTotalPrice()));
            row.put("unit", "元");
            if (doc.getApplyTime() != null) {
                row.put("applyTime", doc.getApplyTime().toString());
            } else {
                row.put("applyTime", "—");
            }
            rows.add(row);
            if (doc.getTotalPrice() != null) {
                totalAmount = totalAmount.add(doc.getTotalPrice());
            }
        }
        if (rows.isEmpty()) {
            rows.add(stubRow());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", formatDateRange(dateFrom, dateTo));
        summary.put("documentCount", page.getNumberOfElements());
        summary.put("totalAmount", formatMoney(totalAmount));
        if (page.getTotalElements() > MAX_ROWS) {
            summary.put("truncated", true);
            summary.put("truncatedHint", "仅展示前 " + MAX_ROWS + " 条");
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    private static Map<String, Object> stubRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("busNo", "—");
        row.put("typeEnum", "—");
        row.put("statusEnum", "—");
        row.put("purposeLocationId", "—");
        row.put("itemQuantity", "0");
        row.put("totalPrice", "0.00");
        row.put("unit", "元");
        row.put("applyTime", "—");
        return row;
    }

    private static String formatDateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return from.toString();
        }
        return from + "~" + to;
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

    private static LocalDate resolveDate(Map<String, Object> params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof LocalDate date) {
                return date;
            }
            if (raw instanceof LocalDateTime dateTime) {
                return dateTime.toLocalDate();
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return LocalDate.parse(text.trim());
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return null;
    }
}
