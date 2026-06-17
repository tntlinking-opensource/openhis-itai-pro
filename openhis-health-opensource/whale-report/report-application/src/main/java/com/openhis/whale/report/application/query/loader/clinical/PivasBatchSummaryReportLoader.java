package com.openhis.whale.report.application.query.loader.clinical;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 配液汇总单（PIVAS-02）。 */
@Component
public class PivasBatchSummaryReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "pivas_batch_summary";

    public PivasBatchSummaryReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "配液汇总单";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("batchNo", "批次号", "left", 140),
                col("bagCount", "袋数", "right", 80),
                col("totalVolume", "总量", "right", 100));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(row("—", "0", "0"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        return Map.of("batchCount", 0);
    }

    private static Map<String, Object> row(String batchNo, String bagCount, String volume) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchNo", batchNo);
        row.put("bagCount", bagCount);
        row.put("totalVolume", volume);
        return row;
    }
}
