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
 * 住院日结（IP-34）C 类报表 Loader。
 */
@Component
public class InpatientDailySettlementLoader extends AbstractSettlementReportLoader {

    private final ReportLoaderDataSupport dataSupport;

    public InpatientDailySettlementLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    /**
     * 出厂报表编码。
     *
     * @return 与 {@code rpt_definition.report_code} 一致的编码
     */
    @Override
    public String reportCode() {
        return ReportLoaderCodes.INPATIENT_DAILY_SETTLEMENT;
    }

    /**
     * 报表展示名称。
     *
     * @return 前端列表与打印标题使用的名称
     */
    @Override
    public String reportName() {
        return "住院日结报表";
    }

    /**
     * 默认列定义（Port 未返回列时使用）。
     *
     * @return 列元数据列表
     */
    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("wardName", "病区", "left", 120),
                col("category", "费用类别", "left", 140),
                col("amount", "金额", "right", 120),
                col("unit", "单位", "center", 64));
    }

    /**
     * 住院日结：优先 Port 真数据，否则空表。
     */
    @Override
    public ReportQueryPageResultDto load(ReportQueryPageRequest request) {
        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(ReportLoaderCodes.INPATIENT_DAILY_SETTLEMENT);
        result.setReportName(reportName());
        result.setColumns(columns());

        if (dataSupport.applyPortDataIfPresent(ReportLoaderCodes.INPATIENT_DAILY_SETTLEMENT, request, result)) {
            // Port 真数据需附带住院日结 PDF 模板编码
            Map<String, Object> summary = new LinkedHashMap<>();
            if (result.getSummary() != null) {
                summary = new LinkedHashMap<>(result.getSummary());
            }
            summary.putIfAbsent("pdfTemplateCode", ReportPdfPrintCodes.INPATIENT_DAILY_SETTLEMENT);
            result.setSummary(summary);
            return result;
        }

        return pageResult(request, List.of(), Map.of());
    }
}
