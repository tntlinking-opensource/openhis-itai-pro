package com.openhis.whale.report.application.report.loader.financial;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/** 发票/票据使用明细（FIN-02）。 */
@Component
public class InvoiceUsageReportLoader extends DatasetFirstReportLoader {

    public InvoiceUsageReportLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.INVOICE_USAGE;
    }

    @Override
    public String reportName() {
        return "发票/票据使用明细";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("busNo", "票据号", "left", 140),
                col("typeCode", "类型", "left", 100),
                col("statusEnum", "状态", "center", 100),
                col("amount", "金额", "right", 120),
                col("issuedAt", "开具时间", "left", 160));
    }
}
