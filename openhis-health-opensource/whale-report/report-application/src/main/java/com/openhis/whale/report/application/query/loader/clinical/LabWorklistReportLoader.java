package com.openhis.whale.report.application.query.loader.clinical;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检验工作清单（LAB-05）。 */
@Component
public class LabWorklistReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "lab_worklist";

    public LabWorklistReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "检验工作清单";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("workStep", "环节", "left", 140),
                col("sampleHint", "说明", "left", 200),
                col("itemCount", "数量", "right", 80));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(row("—", "待 LIS 接入", "0"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        return Map.of("hint", "外系统 LIS 待接入");
    }

    private static Map<String, Object> row(String step, String hint, String count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("workStep", step);
        row.put("sampleHint", hint);
        row.put("itemCount", count);
        return row;
    }
}
