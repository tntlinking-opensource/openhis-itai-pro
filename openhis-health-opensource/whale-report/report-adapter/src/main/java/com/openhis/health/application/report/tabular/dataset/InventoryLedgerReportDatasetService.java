/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.tabular.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存台账 C 类报表数据集（PH-03，现库快照）。
 */
@Service
@RequiredArgsConstructor
public class InventoryLedgerReportDatasetService {

    private static final int MAX_ROWS = 500;

    private final InventoryItemQueryService inventoryItemQueryService;    /**
     * 组装 C 类报表数据集 root Map（rows + summary）。
     *
     * @param params 查询参数（如 dateFrom、dateTo、deptId 等）
     * @return 含 rows、summary 的 root Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        WarehouseInventoryDetailPageQuery query = new WarehouseInventoryDetailPageQuery();
        if (params != null && params.get("keyword") instanceof String keyword && StringUtils.isNotBlank(keyword)) {
            query.setKeyword(keyword.trim());
        }

        List<WarehouseInventoryDetailItemModel> items =
                inventoryItemQueryService.queryWarehouseInventoryDetailTopN(
                        query, MAX_ROWS, Sort.by(Sort.Order.asc("name")));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        for (WarehouseInventoryDetailItemModel item : items) {
            if (item == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", StringUtils.defaultIfBlank(item.getName(), "—"));
            row.put("lotNumber", StringUtils.defaultIfBlank(item.getLotNumber(), "—"));
            row.put("quantity", formatQty(item.getQuantity()));
            row.put("unitCode", StringUtils.defaultIfBlank(item.getUnitCode(), "—"));
            row.put("warehouseId", item.getWarehouseId() != null
                    ? String.valueOf(item.getWarehouseId())
                    : "—");
            if (item.getExpirationDate() != null) {
                row.put("expirationDate", item.getExpirationDate().toLocalDate().toString());
            } else {
                row.put("expirationDate", "—");
            }
            rows.add(row);
            if (item.getQuantity() != null) {
                totalQty = totalQty.add(item.getQuantity());
            }
        }
        if (rows.isEmpty()) {
            rows.add(stubRow());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("asOfDate", LocalDate.now().toString());
        summary.put("rowCount", rows.size());
        summary.put("totalQuantity", formatQty(totalQty));
        if (items.size() >= MAX_ROWS) {
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
        row.put("name", "—");
        row.put("lotNumber", "—");
        row.put("quantity", "0");
        row.put("unitCode", "—");
        row.put("warehouseId", "—");
        row.put("expirationDate", "—");
        return row;
    }

    private static String formatQty(BigDecimal value) {
        BigDecimal safe = ReportDatasetTextSupport.zeroIfNull(value);
        return safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
