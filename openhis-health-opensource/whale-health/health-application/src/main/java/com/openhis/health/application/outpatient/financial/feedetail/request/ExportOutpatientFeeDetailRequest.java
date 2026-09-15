/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 导出门诊费用明细请求（IF-OP-FDT-03）；筛选字段与 IF-OP-FDT-02 同源。
 */
@Data
@Accessors(chain = true)
@Schema(description = "导出门诊费用明细请求")
public class ExportOutpatientFeeDetailRequest {

    /** 结算业务窗起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时为当日 00:00:00）。 */
    @Schema(description = "结算业务窗起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 结算业务窗止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时为当日 23:59:59）。 */
    @Schema(description = "结算业务窗止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /** 挂号科室主键（遗留；报表请用 {@link #requestingOrgId}）。 */
    @Schema(description = "挂号科室主键（遗留）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long registrationDepartmentId;

    /** 开立科室主键（账单 requesting_org_id）。 */
    @Schema(description = "开立科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestingOrgId;

    /** 费用大类多选 code。 */
    @Schema(description = "费用大类 code 多选")
    private List<String> feeCategoryCodes;

    /** 产品细分类字典 code 多选。 */
    @Schema(description = "细分类 code 多选")
    private List<String> subCategoryCodes;

    /** 患者关键字。 */
    @Schema(description = "患者关键字")
    private String patientKeyword;

    /** 项目名称关键字。 */
    @Schema(description = "项目名称关键字")
    private String productNameKeyword;

    /** 项目编码关键字。 */
    @Schema(description = "项目编码关键字")
    private String productCodeKeyword;

    /** 收费人主键。 */
    @Schema(description = "收费人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cashierId;

    /** 收退 Tab code。 */
    @Schema(description = "收退 Tab code")
    private String tradeDirection;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /**
     * 前端下发的导出列；空则导出全部 {@code @Excel} 列。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
