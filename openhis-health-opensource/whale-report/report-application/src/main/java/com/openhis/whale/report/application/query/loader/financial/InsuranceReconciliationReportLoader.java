package com.openhis.whale.report.application.query.loader.financial;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 医保对账表（FIN-03）。 */
@Component
public class InsuranceReconciliationReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "insurance_reconciliation";

    public InsuranceReconciliationReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "医保对账表";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("paymentNo", "支付单号", "left", 140),
                col("kindEnum", "业务类型", "left", 120),
                col("ybSettleIds", "医保结算ID", "left", 160),
                col("tenderedAmount", "实收", "right", 100),
                col("returnedAmount", "已退", "right", 100),
                col("billDate", "账单日", "left", 120));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(row("—", "—", "—", "0.00", "0.00", "—"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        return Map.of("ybPaymentCount", 0, "netAmount", "0.00");
    }

    private static Map<String, Object> row(
            String paymentNo, String kind, String ybIds, String tendered, String returned, String billDate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("paymentNo", paymentNo);
        row.put("kindEnum", kind);
        row.put("ybSettleIds", ybIds);
        row.put("tenderedAmount", tendered);
        row.put("returnedAmount", returned);
        row.put("billDate", billDate);
        return row;
    }
}
