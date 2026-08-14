/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院费用明细报表多维净汇总请求（IF-RPT-IFD-02；收退方向强制 ALL）。
 *
 * <p>汇总维由 {@code summaryDimension} 指定（默认 FIN_TYPE）；金额口径见 AS-RPT-009。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院费用明细报表汇总请求")
public class SummarizeInpatientFeeDetailReportRequest {

    /** 结算业务日起（yyyy-MM-dd，闭区间）。 */
    @Schema(description = "结算业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 结算业务日止（yyyy-MM-dd，闭区间）。 */
    @Schema(description = "结算业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /** 住院科室主键（遗留；报表中心请用 {@link #requestingOrgId}）。 */
    @Schema(description = "住院科室主键（遗留）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long registrationDepartmentId;

    /** 开立科室主键（账单 requesting_org_id）。 */
    @Schema(description = "开立科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestingOrgId;

    /** 费用大类多选 code。 */
    @Schema(description = "费用大类 code 多选")
    private List<String> feeCategoryCodes;

    /** 细分类（fin_type）多选 code。 */
    @Schema(description = "细分类 code 多选")
    private List<String> subCategoryCodes;

    /** 患者姓名或住院号关键字。 */
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

    /**
     * 结算状态筛：ALL / SETTLED / PENDING_SETTLEMENT；空则默认 ALL。
     */
    @Schema(description = "结算状态筛 ALL/SETTLED/PENDING_SETTLEMENT")
    private String settlementStatus;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /**
     * 汇总维度：FIN_TYPE / DEPARTMENT / FEE_CATEGORY / ITEM；空则默认 FIN_TYPE。
     */
    @Schema(description = "汇总维度 FIN_TYPE/DEPARTMENT/FEE_CATEGORY/ITEM")
    private String summaryDimension;
}
