package com.openhis.whale.report.application.report.loader.financial;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/** 医保对账表（FIN-03）。 */
@Component
public class InsuranceReconciliationReportLoader extends DatasetFirstReportLoader {

    public InsuranceReconciliationReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.INSURANCE_RECONCILIATION;
    }

    @Override
    public String reportName() {
        return "医保对账表";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("paymentNo", "支付单号", "left", 140),
                col("kindEnum", "业务类型", "left", 120),
                col("ybSettleIds", "医保结算ID", "left", 160),
                col("tenderedAmount", "实收", "right", 100),
                col("returnedAmount", "已退", "right", 100),
                col("billDate", "账单日", "left", 120));
    }
}
