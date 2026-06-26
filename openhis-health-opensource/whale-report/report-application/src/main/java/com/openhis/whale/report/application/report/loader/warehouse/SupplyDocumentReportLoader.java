package com.openhis.whale.report.application.report.loader.warehouse;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.support.DatasetFirstReportLoader;
import com.openhis.whale.report.application.report.support.ReportLoaderDataSupport;
import com.openhis.whale.report.contracts.catalog.supply.SupplyDocumentReportSpec;

import java.util.List;

/**
 * 按 {@link com.openhis.health.domain.share.enums.SupplyType} 拆分的供应单据 C 类 Loader。
 */
public class SupplyDocumentReportLoader extends DatasetFirstReportLoader {

    private final SupplyDocumentReportSpec spec;

    public SupplyDocumentReportLoader(SupplyDocumentReportSpec spec, ReportLoaderDataSupport dataSupport) {
        super(dataSupport);
        this.spec = spec;
    }

    @Override
    public String reportCode() {
        return spec.reportCode();
    }

    @Override
    public String reportName() {
        return spec.reportName();
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("busNo", "单据号", "left", 140),
                col("typeEnum", "类型", "left", 120),
                col("statusEnum", "状态", "center", 100),
                col("purposeLocationId", "目标库位", "left", 100),
                col("itemQuantity", "数量", "right", 100),
                col("totalPrice", "金额", "right", 120),
                col("applyTime", "申请时间", "left", 160));
    }
}
