package com.openhis.whale.report.application.query.loader.financial;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 发票/票据使用明细（FIN-02）。 */
@Component
public class InvoiceUsageReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "invoice_usage_report";

    public InvoiceUsageReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "发票/票据使用明细";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("busNo", "票据号", "left", 140),
                col("typeCode", "类型", "left", 100),
                col("statusEnum", "状态", "center", 100),
                col("amount", "金额", "right", 120),
                col("issuedAt", "开具时间", "left", 160));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(row("—", "—", "—", "0.00", "—"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        return Map.of("invoiceCount", 0, "totalAmount", "0.00");
    }

    private static Map<String, Object> row(String busNo, String type, String status, String amount, String issuedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("busNo", busNo);
        row.put("typeCode", type);
        row.put("statusEnum", status);
        row.put("amount", amount);
        row.put("issuedAt", issuedAt);
        return row;
    }
}
