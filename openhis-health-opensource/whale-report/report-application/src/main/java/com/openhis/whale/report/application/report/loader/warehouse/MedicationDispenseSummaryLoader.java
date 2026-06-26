package com.openhis.whale.report.application.report.loader.warehouse;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 发药汇总单（PH-01）C 类报表 Loader。
 */
@Component
public class MedicationDispenseSummaryLoader extends DatasetFirstReportLoader {

    public MedicationDispenseSummaryLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.MEDICATION_DISPENSE_SUMMARY;
    }

    @Override
    public String reportName() {
        return "发药汇总单";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("locationId", "药房/库位", "left", 140),
                col("dispenseCount", "发药笔数", "right", 100),
                col("totalQuantity", "发药数量", "right", 120),
                col("unit", "单位", "center", 64));
    }
}
