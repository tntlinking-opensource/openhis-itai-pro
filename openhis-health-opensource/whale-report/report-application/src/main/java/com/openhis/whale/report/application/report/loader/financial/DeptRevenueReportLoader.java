package com.openhis.whale.report.application.report.loader.financial;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 科室收入统计（FIN-01）C 类报表 Loader。
 */
@Component
public class DeptRevenueReportLoader extends DatasetFirstReportLoader {

    public DeptRevenueReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.DEPT_REVENUE;
    }

    @Override
    public String reportName() {
        return "科室收入统计";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("deptName", "科室", "left", 160),
                col("incomeAmount", "收入金额", "right", 120),
                col("unit", "单位", "center", 64));
    }
}
