package com.openhis.whale.report.application.query.loader.financial;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 收费员缴款日报（OP-06）。 */
@Component
public class CashierDailyReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "cashier_daily_report";

    public CashierDailyReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "收费员缴款日报";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("cashierId", "收费员", "left", 120),
                col("paymentCount", "笔数", "right", 80),
                col("amount", "金额", "right", 120),
                col("unit", "单位", "center", 64));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(row("—", "0", "0.00"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAmount", "0.00");
        return summary;
    }

    private static Map<String, Object> row(String cashierId, String count, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cashierId", cashierId);
        row.put("paymentCount", count);
        row.put("amount", amount);
        row.put("unit", "元");
        return row;
    }
}
