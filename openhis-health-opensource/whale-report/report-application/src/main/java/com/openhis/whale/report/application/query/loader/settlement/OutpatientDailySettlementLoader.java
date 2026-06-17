package com.openhis.whale.report.application.query.loader.settlement;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门诊日结（OP-05）C 类报表 Loader（出厂样例数据，后续可替换为收费域聚合）。
 */
@Component
public class OutpatientDailySettlementLoader extends AbstractSettlementReportLoader {

    public static final String REPORT_CODE = "outpatient_daily_settlement";
    private static final String PDF_TEMPLATE = "outpatient_daily_settlement_pdf";

    private final ReportLoaderDataSupport dataSupport;

    public OutpatientDailySettlementLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "门诊日结报表";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("category", "费用类别", "left", 160),
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

        Map<String, Object> summary = buildIncomeSummary();
        summary.put("scope", "outpatient");
        summary.put("dateRange", resolveDateRangeText(request));
        summary.put("cashier", trimToEmpty(request.getKeyword()));
        summary.put("pdfTemplateCode", PDF_TEMPLATE);
        return pageResult(request, buildFeeCategorySummaryRows(), summary);
    }
}
