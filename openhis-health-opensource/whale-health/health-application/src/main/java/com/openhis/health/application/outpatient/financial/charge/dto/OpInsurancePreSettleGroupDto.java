/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 医保预结算分组预览（混合结算展示）。 */
@Data
@Accessors(chain = true)
@Schema(description = "医保预结算分组")
public class OpInsurancePreSettleGroupDto {

    /** 组序号（从 1 起）。 */
    @Schema(description = "组序号")
    private Integer groupIndex;
    /** 医疗类别编码。 */
    @Schema(description = "医疗类别编码")
    private String medType;
    /** 医疗类别展示名。 */
    @Schema(description = "医疗类别展示名")
    private String medTypeDisplay;
    /** 慢病诊断医保码。 */
    @Schema(description = "慢病诊断医保码")
    private String diseCodg;
    /** 慢病诊断名称。 */
    @Schema(description = "慢病诊断名称")
    private String diseName;
    /** 医保合同主键。 */
    @Schema(description = "合同主键")
    private Long contractId;
    /** 记账账户主键。 */
    @Schema(description = "记账账户主键")
    private Long accountId;
    /** 本组收费项 id 列表。 */
    @Schema(description = "本组收费项")
    private List<Long> chargeItemIds;
    /** 医保收费批次号。 */
    @Schema(description = "医保收费批次号")
    private String chrgBchno;
    /** 平台就诊 id（mdtrt_id）。 */
    @Schema(description = "平台就诊 ID")
    private String mdtrtId;
    /** 预结算快照主键。 */
    @Schema(description = "预结算快照主键")
    private Long preSettleId;
    /** 医疗费总额。 */
    @Schema(description = "医疗费总额")
    private BigDecimal medfeeSumamt;
    /** 基金支付总额（fund_pay_sumamt；含统筹及其他基金，不等于单列统筹）。 */
    @Schema(description = "基金支付总额")
    private BigDecimal fundPaySumamt;
    /** 个人账户支付金额。 */
    @Schema(description = "个账支付")
    private BigDecimal acctPay;
    /** 个人现金支付金额（混合结算患者应付）。 */
    @Schema(description = "个人现金")
    private BigDecimal psnCashPay;
}
