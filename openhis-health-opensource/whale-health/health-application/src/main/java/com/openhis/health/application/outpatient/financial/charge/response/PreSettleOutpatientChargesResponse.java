/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.response;

import com.openhis.health.application.outpatient.financial.charge.dto.OpInsurancePreSettleGroupDto;
import com.openhis.health.application.outpatient.financial.charge.dto.OpSelfPayRollupDto;
import com.openhis.health.application.outpatient.financial.charge.dto.PreSettleChargeLinePreviewDto;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊预结算响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊预结算响应")
public class PreSettleOutpatientChargesResponse {
    /** 支付草稿主键（业务字段）。 */
    @Schema(description = "支付草稿主键")
    private Long paymentDraftId;
    /** 展示应收金额（业务字段）。 */
    @Schema(description = "展示应收金额")
    private BigDecimal displayAmount;
    /** 预结算行预览。 */
    @Schema(description = "预结算行预览")
    private List<PreSettleChargeLinePreviewDto> previews;
    /** 医保收费批次号（2206 成功后返回）。 */
    @Schema(description = "医保收费批次号")
    private String ybChrgBchno;
    /** 医保医疗费总额。 */
    @Schema(description = "医保医疗费总额")
    private BigDecimal ybMedfeeSumamt;
    /** 医保统筹支付。 */
    @Schema(description = "医保统筹支付")
    private BigDecimal ybFundPaySumamt;
    /** 医保个账支付。 */
    @Schema(description = "医保个账支付")
    private BigDecimal ybAcctPay;
    /** 医保现金/自付。 */
    @Schema(description = "医保现金自付")
    private BigDecimal ybPsnCashPay;
    /** 是否已完成医保 2206 预结算。 */
    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;
    /** MIXED | INSURANCE_ONLY | SELF_PAY_ONLY | WORK_INJURY_INVOKE */
    @Schema(description = "结算构成")
    private String settlementComposition;
    /** 工伤 invoke 首步载荷（settlementComposition=WORK_INJURY_INVOKE 时非空）。 */
    @Schema(description = "工伤 invoke 首步载荷")
    private PrepareWorkInjuryInvokeExtensionOut workInjuryInvokeStep;
    /** 医保分组明细 */
    @Schema(description = "医保预结算分组")
    private List<OpInsurancePreSettleGroupDto> insuranceGroups;
    /** 自费汇总 */
    @Schema(description = "自费汇总")
    private OpSelfPayRollupDto selfPayRollup;
}
