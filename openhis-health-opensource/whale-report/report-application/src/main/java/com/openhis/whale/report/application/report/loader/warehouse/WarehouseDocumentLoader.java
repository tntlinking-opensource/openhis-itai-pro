package com.openhis.whale.report.application.report.loader.warehouse;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 出库/入库单（PH-02）C 类报表 Loader。
 */
@Component
public class WarehouseDocumentLoader extends DatasetFirstReportLoader {

    public WarehouseDocumentLoader(ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
    }

    @Override
    public String reportCode() {
        return ReportLoaderCodes.WAREHOUSE_DOCUMENT;
    }

    @Override
    public String reportName() {
        return "出库/入库单";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("busNo", "单据号", "left", 140),
                col("typeEnum", "类型", "left", 100),
                col("statusEnum", "状态", "center", 100),
                col("purposeLocationId", "目标库位", "left", 100),
                col("itemQuantity", "数量", "right", 100),
                col("totalPrice", "金额", "right", 120),
                col("applyTime", "申请时间", "left", 160));
    }
}
