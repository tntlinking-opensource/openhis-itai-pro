package com.openhis.whale.report.application.query.loader.warehouse;

import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.contracts.supply.SupplyDocumentReportSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@link com.openhis.health.domain.share.enums.SupplyType} 拆分的供应单据 C 类 Loader。
 */
public class SupplyDocumentReportLoader extends PortFirstReportLoader {

    private final SupplyDocumentReportSpec spec;

    public SupplyDocumentReportLoader(SupplyDocumentReportSpec spec, ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
        this.spec = spec;
    }

    @Override
    public String reportCode() {
        return spec.reportCode();
    }

    @Override
    public String reportName() {
        return spec.reportName();
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("busNo", "单据号", "left", 140),
                col("typeEnum", "类型", "left", 120),
                col("statusEnum", "状态", "center", 100),
                col("purposeLocationId", "目标库位", "left", 100),
                col("itemQuantity", "数量", "right", 100),
                col("totalPrice", "金额", "right", 120),
                col("applyTime", "申请时间", "left", 160));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(stubRow());
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("documentCount", 0);
        summary.put("totalAmount", "0.00");
        return summary;
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
}
