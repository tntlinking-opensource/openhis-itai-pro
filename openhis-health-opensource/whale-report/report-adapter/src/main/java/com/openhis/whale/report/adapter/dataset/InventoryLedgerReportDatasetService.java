/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.dataset;
import com.openhis.whale.report.adapter.support.ReportDatasetTextSupport;

import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.whale.report.contracts.constants.ReportDatasetLimits;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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

    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 组装 C 类报表数据集 root Map（rows + summary）。
     *
     * @param params 查询参数（如 dateFrom、dateTo、deptId 等）
     * @return 含 rows、summary 的 root Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        WarehouseInventoryDetailPageQuery query = new WarehouseInventoryDetailPageQuery();
        if (params != null && params.get("keyword") instanceof String keyword && StringUtils.isNotEmpty(keyword)) {
            // 关键字过滤库房物资名称
            query.setKeyword(keyword.trim());
        }

        List<WarehouseInventoryDetailItemModel> items =
                inventoryItemQueryService.queryWarehouseInventoryDetailTopN(
                        query, ReportDatasetLimits.MAX_TABULAR_ROWS, Sort.by(Sort.Order.asc("name")));

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
            row.put("warehouseId", ReportDatasetTextSupport.formatLongOrDash(item.getWarehouseId()));
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
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("asOfDate", LocalDate.now().toString());
        summary.put("rowCount", rows.size());
        summary.put("totalQuantity", formatQty(totalQty));
        if (items.size() >= ReportDatasetLimits.MAX_TABULAR_ROWS) {
            // 命中行数上限，提示前端截断
            summary.put("truncated", true);
            summary.put("truncatedHint", "仅展示前 " + ReportDatasetLimits.MAX_TABULAR_ROWS + " 条");
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    private static String formatQty(BigDecimal value) {
        BigDecimal safe = ReportDatasetTextSupport.zeroIfNull(value);
        return safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
