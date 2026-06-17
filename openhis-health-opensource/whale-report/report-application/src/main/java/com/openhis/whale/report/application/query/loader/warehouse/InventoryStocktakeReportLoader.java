package com.openhis.whale.report.application.query.loader.warehouse;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 盘点表（PH-05）。 */
@Component
public class InventoryStocktakeReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "inventory_stocktake";

    public InventoryStocktakeReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "盘点表";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("busNo", "单据号", "left", 140),
                col("typeEnum", "类型", "left", 120),
                col("statusEnum", "状态", "center", 100),
                col("itemQuantity", "盈亏数量", "right", 100),
                col("totalPrice", "金额", "right", 120),
                col("applyTime", "申请时间", "left", 160));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(row("—", "—", "—", "0", "0.00", "—"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        return Map.of("documentCount", 0, "totalAmount", "0.00");
    }

    private static Map<String, Object> row(
            String busNo, String type, String status, String qty, String amount, String time) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("busNo", busNo);
        row.put("typeEnum", type);
        row.put("statusEnum", status);
        row.put("itemQuantity", qty);
        row.put("totalPrice", amount);
        row.put("applyTime", time);
        return row;
    }
}
