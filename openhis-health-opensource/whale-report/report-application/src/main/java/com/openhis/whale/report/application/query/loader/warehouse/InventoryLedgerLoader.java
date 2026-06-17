package com.openhis.whale.report.application.query.loader.warehouse;
import com.openhis.whale.report.application.query.support.ReportQueryPaging;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.ReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存台账（PH-03）C 类报表 Loader。
 */
@Component
public class InventoryLedgerLoader implements ReportLoader {

    public static final String REPORT_CODE = "inventory_ledger";

    private final ReportLoaderDataSupport dataSupport;

    public InventoryLedgerLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "库存台账";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("name", "品名", "left", 160),
                col("lotNumber", "批号", "left", 120),
                col("quantity", "数量", "right", 100),
                col("unitCode", "单位", "center", 80),
                col("warehouseId", "库房", "left", 100),
                col("expirationDate", "效期", "left", 120));
    }

    @Override
    public ReportQueryPageResultDto load(ReportQueryPageRequest request) {
        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(REPORT_CODE);
        result.setReportName(reportName());
        result.setColumns(columns());

        if (dataSupport.applyPortDataIfPresent(REPORT_CODE, request, result)) {
            return result;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("asOfDate", "—");
        summary.put("rowCount", 0);
        summary.put("totalQuantity", "0");
        return pageResult(request, List.of(stubRow()), summary);
    }

    private static ReportColumnDto col(String field, String label, String align, int width) {
        ReportColumnDto dto = new ReportColumnDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setAlign(align);
        dto.setWidth(width);
        return dto;
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

    private static ReportQueryPageResultDto pageResult(
            ReportQueryPageRequest request,
            List<Map<String, Object>> allRows,
            Map<String, Object> summary) {
        int pageNo = Math.max(request.getPageNo(), 1);
        int pageSize = Math.max(request.getPageSize(), 1);
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, allRows.size());

        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(REPORT_CODE);
        result.setReportName("库存台账");
        result.setRows(ReportQueryPaging.slice(allRows, from, to));
        result.setTotalCount(allRows.size());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setSummary(summary);
        return result;
    }
}
