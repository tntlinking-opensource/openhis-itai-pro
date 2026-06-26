package com.openhis.whale.report.application.report.loader.warehouse;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存台账（PH-03）C 类报表 Loader。
 */
@Component
public class InventoryLedgerLoader extends DatasetFirstReportLoader {

    public InventoryLedgerLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.INVENTORY_LEDGER;
    }

    @Override
    public String reportName() {
        return "库存台账";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("name", "品名", "left", 160),
                col("lotNumber", "批号", "left", 120),
                col("quantity", "数量", "right", 100),
                col("unitCode", "单位", "center", 80),
                col("warehouseId", "库房", "left", 100),
                col("expirationDate", "效期", "left", 120));
    }
}
