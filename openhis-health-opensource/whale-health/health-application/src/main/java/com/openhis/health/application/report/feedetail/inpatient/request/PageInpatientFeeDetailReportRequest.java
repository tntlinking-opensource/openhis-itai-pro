/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院费用明细报表明细分页请求（IF-RPT-IFD-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院费用明细报表明细分页请求")
public class PageInpatientFeeDetailReportRequest extends AbstractHealthPageRequest {

    /** 结算业务日起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59，闭区间）。 */
    @Schema(description = "结算业务日起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 结算业务日止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59，闭区间）。 */
    @Schema(description = "结算业务日止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）", requiredMode = Schema.RequiredMode.REQUIRED)
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

    /** 收退 Tab：ALL / CHARGE / REFUND；默认 ALL。 */
    @Schema(description = "收退 Tab code")
    private String tradeDirection;

    /**
     * 结算状态筛：ALL / SETTLED / PENDING_SETTLEMENT；空则默认 ALL。
     */
    @Schema(description = "结算状态筛 ALL/SETTLED/PENDING_SETTLEMENT")
    private String settlementStatus;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /**
     * 明细行合并模式：{@code LINE}（或不传/空）= 不合并；{@code BY_DEFINITION} = 同就诊块内按
     * {@code ChargeItem.definitionId} 合并金额列。非法非空值由 AppService Gate 抛 {@code EX-RPT-IFD-102}。
     *
     * <p>仅影响本页就诊块装配后的行展示；页脚全量 summary 路径不变。</p>
     */
    @Schema(description = "明细行合并模式 LINE/BY_DEFINITION；空或 LINE 不合并")
    private String lineMergeMode;
}
