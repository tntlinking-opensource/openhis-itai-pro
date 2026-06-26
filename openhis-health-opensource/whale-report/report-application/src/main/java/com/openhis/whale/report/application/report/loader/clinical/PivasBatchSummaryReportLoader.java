package com.openhis.whale.report.application.report.loader.clinical;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/** 配液汇总单（PIVAS-02）。 */
@Component
public class PivasBatchSummaryReportLoader extends DatasetFirstReportLoader {

    public PivasBatchSummaryReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.PIVAS_BATCH_SUMMARY;
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
}
