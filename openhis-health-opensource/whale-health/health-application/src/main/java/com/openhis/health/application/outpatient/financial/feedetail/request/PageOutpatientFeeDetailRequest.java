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

    /** 结算业务窗起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时为当日 00:00:00）。 */
    @Schema(description = "结算业务窗起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 结算业务窗止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时为当日 23:59:59）。 */
    @Schema(description = "结算业务窗止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /** 挂号科室主键（Encounter.organization_id；遗留）。 */
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
