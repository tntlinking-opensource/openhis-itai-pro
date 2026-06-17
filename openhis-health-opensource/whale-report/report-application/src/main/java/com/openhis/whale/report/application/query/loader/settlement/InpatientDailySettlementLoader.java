package com.openhis.whale.report.application.query.loader.settlement;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院日结（IP-34）C 类报表 Loader。
 */
@Component
public class InpatientDailySettlementLoader extends AbstractSettlementReportLoader {

    public static final String REPORT_CODE = "inpatient_daily_settlement";
    private static final String PDF_TEMPLATE = "inpatient_daily_settlement_pdf";

    private final ReportLoaderDataSupport dataSupport;

    public InpatientDailySettlementLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "住院日结报表";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("wardName", "病区", "left", 120),
                col("category", "费用类别", "left", 140),
                col("amount", "金额", "right", 120),
                col("unit", "单位", "center", 64));
    }

    @Override
    public ReportQueryPageResultDto load(ReportQueryPageRequest request) {
        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(REPORT_CODE);
        result.setReportName(reportName());
        result.setColumns(columns());

        if (dataSupport.applyPortDataIfPresent(REPORT_CODE, request, result)) {
            Map<String, Object> summary = result.getSummary() != null
                    ? new LinkedHashMap<>(result.getSummary())
                    : new LinkedHashMap<>();
            summary.putIfAbsent("pdfTemplateCode", PDF_TEMPLATE);
            result.setSummary(summary);
            return result;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(inpatientRow("内科一病区", "药品", "1280.50"));
        rows.add(inpatientRow("内科一病区", "诊疗", "560.00"));
        rows.add(inpatientRow("内科一病区", "耗材", "88.20"));
        rows.add(inpatientRow("外科病区", "药品", "920.10"));
        rows.add(inpatientRow("外科病区", "检查", "430.00"));

        Map<String, Object> summary = buildIncomeSummary();
        summary.put("scope", "inpatient");
        summary.put("dateRange", resolveDateRangeText(request));
        summary.put("pdfTemplateCode", PDF_TEMPLATE);
        return pageResult(request, rows, summary);
    }

    private static Map<String, Object> inpatientRow(String wardName, String category, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("wardName", wardName);
        row.put("category", category);
        row.put("amount", amount);
        row.put("unit", "元");
        return row;
    }
}
