package com.openhis.whale.report.application.report.loader.clinical;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/** 医嘱执行单批量（IP-09）。 */
@Component
public class OrderExecuteBatchReportLoader extends DatasetFirstReportLoader {

    public OrderExecuteBatchReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.ORDER_EXECUTE_BATCH;
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
}
