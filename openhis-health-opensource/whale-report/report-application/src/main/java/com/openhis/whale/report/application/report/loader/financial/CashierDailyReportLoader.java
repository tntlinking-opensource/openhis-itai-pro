package com.openhis.whale.report.application.report.loader.financial;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 收费员缴款日报（OP-06）C 类报表 Loader。
 */
@Component
public class CashierDailyReportLoader extends DatasetFirstReportLoader {

    public CashierDailyReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.CASHIER_DAILY;
    }

    @Override
    public String reportName() {
        return "收费员缴款日报";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("cashierId", "收费员", "left", 120),
                col("paymentCount", "笔数", "right", 80),
                col("amount", "金额", "right", 120),
                col("unit", "单位", "center", 64));
    }
}
