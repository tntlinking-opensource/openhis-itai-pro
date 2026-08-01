/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分页查询门诊费用明细请求（IF-OP-FDT-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询门诊费用明细请求")
public class PageOutpatientFeeDetailRequest extends AbstractHealthPageRequest {

    /** 结算业务日起（yyyy-MM-dd，闭区间）。 */
    @Schema(description = "结算业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 结算业务日止（yyyy-MM-dd，闭区间）。 */
    @Schema(description = "结算业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /** 挂号科室主键（Encounter.organization_id）。 */
    @Schema(description = "挂号科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long registrationDepartmentId;

    /** 费用大类多选 code。 */
    @Schema(description = "费用大类 code 多选")
    private List<String> feeCategoryCodes;

    /** 产品细分类字典 code 多选。 */
    @Schema(description = "细分类 code 多选")
    private List<String> subCategoryCodes;

    /** 患者姓名或门诊号 busNo 模糊。 */
    @Schema(description = "患者关键字")
    private String patientKeyword;

    /** 项目名称模糊；与 productCodeKeyword 双填时 AND。 */
    @Schema(description = "项目名称关键字")
    private String productNameKeyword;

    /** 院内项目编码模糊；与 productNameKeyword 双填时 AND。 */
    @Schema(description = "项目编码关键字")
    private String productCodeKeyword;

    /** 收费人主键（payment.enterer_id）。 */
    @Schema(description = "收费人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cashierId;

    /** 收退 Tab：ALL / CHARGE / REFUND；默认 ALL。 */
    @Schema(description = "收退 Tab code")
    private String tradeDirection;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;
}
