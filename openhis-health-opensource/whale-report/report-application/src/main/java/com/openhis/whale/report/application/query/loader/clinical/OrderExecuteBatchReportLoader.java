package com.openhis.whale.report.application.query.loader.clinical;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.PortFirstReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 医嘱执行单批量（IP-09）。 */
@Component
public class OrderExecuteBatchReportLoader extends PortFirstReportLoader {

    public static final String REPORT_CODE = "order_execute_batch";

    public OrderExecuteBatchReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "医嘱执行单（批量）";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("procedureId", "执行ID", "left", 100),
                col("encounterId", "就诊ID", "left", 100),
                col("categoryEnum", "类别", "left", 100),
                col("statusEnum", "状态", "center", 100),
                col("procedureCode", "项目编码", "left", 120),
                col("occurrenceTime", "执行时间", "left", 160));
    }

    @Override
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of(row("—", "—", "—", "—", "—", "—"));
    }

    @Override
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        return Map.of("executeCount", 0);
    }

    private static Map<String, Object> row(
            String procedureId, String encounterId, String category, String status, String code, String time) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("procedureId", procedureId);
        row.put("encounterId", encounterId);
        row.put("categoryEnum", category);
        row.put("statusEnum", status);
        row.put("procedureCode", code);
        row.put("occurrenceTime", time);
        return row;
    }
}
