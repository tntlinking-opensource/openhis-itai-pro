package com.openhis.whale.report.application.report.loader.settlement;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import com.openhis.whale.report.domain.constants.ReportPdfPrintCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门诊日结（OP-05）C 类报表 Loader。
 */
@Component
public class OutpatientDailySettlementLoader extends AbstractSettlementReportLoader {

    private final ReportLoaderDataSupport dataSupport;

    public OutpatientDailySettlementLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    /**
     * 出厂报表编码。
     *
     * @return 与 {@code rpt_definition.report_code} 一致的编码
     */
    @Override
    public String reportCode() {
        return ReportLoaderCodes.OUTPATIENT_DAILY_SETTLEMENT;
    }

    /**
     * 报表展示名称。
     *
     * @return 前端列表与打印标题使用的名称
     */
    @Override
    public String reportName() {
        return "门诊日结报表";
    }

    /**
     * 默认列定义（Port 未返回列时使用）。
     *
     * @return 列元数据列表
     */
    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("category", "费用类别", "left", 160),
                col("amount", "金额", "right", 120),
                col("unit", "单位", "center", 64));
    }

    /**
     * 门诊日结：优先 Port 真数据，否则空表。
     */
    @Override
    public ReportQueryPageResultDto load(ReportQueryPageRequest request) {
        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(ReportLoaderCodes.OUTPATIENT_DAILY_SETTLEMENT);
        result.setReportName(reportName());
        result.setColumns(columns());

        // Pre：HIS 日结数据集已接线时直接返回
        if (dataSupport.applyPortDataIfPresent(ReportLoaderCodes.OUTPATIENT_DAILY_SETTLEMENT, request, result)) {
            Map<String, Object> summary = new LinkedHashMap<>();
            if (result.getSummary() != null) {
                summary = new LinkedHashMap<>(result.getSummary());
            }
            summary.putIfAbsent("pdfTemplateCode", ReportPdfPrintCodes.OUTPATIENT_DAILY_SETTLEMENT);
            result.setSummary(summary);
            return result;
        }

        return pageResult(request, List.of(), Map.of());
    }
}
