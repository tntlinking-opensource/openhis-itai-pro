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
    /** 医保基金支付总额（含统筹/其他基金等，不等于单列统筹）。 */
    @Schema(description = "医保基金支付总额")
    private BigDecimal ybFundPaySumamt;
    /** 基本医保统筹基金支出（hifp_pay）。 */
    @Schema(description = "基本医保统筹基金支出")
    private BigDecimal ybHifpPay;
    /** 其他支出（oth_pay，含电子钱包等）。 */
    @Schema(description = "其他支出")
    private BigDecimal ybOthPay;
    /** 医保个账支付。 */
    @Schema(description = "医保个账支付")
    private BigDecimal ybAcctPay;
    /** 医保现金/自付。 */
    @Schema(description = "医保现金自付")
    private BigDecimal ybPsnCashPay;
    /**
     * 实际支付起付线（2206 {@code act_pay_dedc}；混合结算多组时金额求和）。
     *
     * <p>仅收银展示，不参与本地应收重算。</p>
     */
    @Schema(description = "实际支付起付线")
    private BigDecimal ybActPayDedc;
    /**
     * 公务员标志（2206 {@code cvlserv_flag}，0=否 / 1=是；混合结算取首笔非空）。
     *
     * <p>仅收银展示。</p>
     */
    @Schema(description = "公务员标志")
    private String ybCvlservFlag;
    /**
     * 公务员医疗补助资金支出（2206 {@code cvlserv_pay}；混合结算多组时金额求和）。
     *
     * <p>仅收银展示，不参与本地应收重算。</p>
     */
    @Schema(description = "公务员医疗补助资金支出")
    private BigDecimal ybCvlservPay;
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

    /**
     * 本批优惠合计（原价−折后）；门控关闭或无打折时为 0（BR-OPC-029）。
     */
    @Schema(description = "本批优惠合计")
    private BigDecimal discountAmountTotal;
}
