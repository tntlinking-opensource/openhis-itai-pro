package com.openhis.whale.report.application.query.loader.financial;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科室收入统计（FIN-01）C 类报表 Loader。
 */
@Component
public class DeptRevenueReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "dept_revenue_report";

    public DeptRevenueReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "科室收入统计";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("deptName", "科室", "left", 160),
                col("incomeAmount", "收入金额", "right", 120),
                col("unit", "单位", "center", 64));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(
                row("内科", "12580.00"),
                row("外科", "9860.50"),
                row("儿科", "4320.00"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalIncome", "26760.50");
        return summary;
    }

    private static Map<String, Object> row(String dept, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deptName", dept);
        row.put("incomeAmount", amount);
        row.put("unit", "元");
        return row;
    }
}
