/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.tabular.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.enums.DispenseStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
 * 发药汇总 C 类报表数据集（PH-01）。
 */
@Service
@RequiredArgsConstructor
public class MedicationDispenseSummaryReportDatasetService {

    private final MedicationDispenseQueryService medicationDispenseQueryService;    /**
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

        MedicationDispenseSlipPageQuery query = new MedicationDispenseSlipPageQuery();
        query.setPageNo(1);
        query.setPageSize(500);
        query.setStartDate(dateFrom.atStartOfDay());
        query.setEndDate(dateTo.atTime(LocalTime.MAX));
        query.setDispenseStatusEnumIn(List.of(
                DispenseStatus.COMPLETED.getCode(),
                DispenseStatus.PART_COMPLETED.getCode()));

        List<MedicationDispenseSlipQueryModel> slips = medicationDispenseQueryService.listMedicationDispenseSlip(query);

        Map<Long, DispenseAgg> byLocation = new LinkedHashMap<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        for (MedicationDispenseSlipQueryModel slip : slips) {
            if (slip == null) {
                continue;
            }
            long locationId = 0L;
            if (slip.getLocationId() != null) {
                locationId = slip.getLocationId();
            }
            DispenseAgg agg = byLocation.computeIfAbsent(locationId, id -> new DispenseAgg(id));
            agg.count += 1;
            BigDecimal qty = slip.getQuantity();
            if (slip.getDispenseQuantity() != null) {
                qty = slip.getDispenseQuantity();
            }
            if (qty != null) {
                agg.quantity = agg.quantity.add(qty);
                totalQty = totalQty.add(qty);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (DispenseAgg agg : byLocation.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("locationId", formatLocationId(agg.locationId));
            row.put("dispenseCount", String.valueOf(agg.count));
            row.put("totalQuantity", formatQty(agg.quantity));
            row.put("unit", "—");
            rows.add(row);
        }
        if (rows.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("locationId", "—");
            row.put("dispenseCount", "0");
            row.put("totalQuantity", "0");
            row.put("unit", "—");
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", formatDateRange(dateFrom, dateTo));
        summary.put("slipCount", slips.size());
        summary.put("totalQuantity", formatQty(totalQty));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    private static String formatDateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return from.toString();
        }
        return from + "~" + to;
    }

    private static String formatQty(BigDecimal value) {
        return ReportDatasetTextSupport.zeroIfNull(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String formatLocationId(long locationId) {
        if (locationId > 0) {
            return String.valueOf(locationId);
        }
        return "—";
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

    private static final class DispenseAgg {
        private final long locationId;
        private int count = 0;
        private BigDecimal quantity = BigDecimal.ZERO;

        private DispenseAgg(long locationId) {
            this.locationId = locationId;
        }
    }
}
