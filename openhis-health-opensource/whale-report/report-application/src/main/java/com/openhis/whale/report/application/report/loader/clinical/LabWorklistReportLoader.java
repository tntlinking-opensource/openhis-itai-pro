package com.openhis.whale.report.application.report.loader.clinical;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/** 检验工作清单（LAB-05）。 */
@Component
public class LabWorklistReportLoader extends DatasetFirstReportLoader {

    public LabWorklistReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.LAB_WORKLIST;
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
}
