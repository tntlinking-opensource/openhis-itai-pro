/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.dailysettlement.dto;

import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊日结患者分项导出行（对齐门诊费用明细：{@code @Excel} + AnnotatedExcelExporter）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊日结患者分项导出行")
public class OutpatientDailySettlementPatientExportRowDto {

    /** 分块序号。 */
    @Excel(name = "序号", sort = 1)
    private Integer seqNo;

    /** 患者姓名。 */
    @Excel(name = "患者姓名", sort = 2)
    private String patientName;

    /** 门诊号。 */
    @Excel(name = "门诊号", sort = 3)
    private String encounterBusNo;

    /** 收费日期（yyyy-MM-dd）。 */
    @Excel(name = "收费日期", sort = 4)
    private String chargeDate;

    /** 是否住院。 */
    @Excel(name = "是否住院", sort = 5)
    private String hospitalizedText;

    /** 总收入。 */
    @Excel(name = "总收入", sort = 6, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal totalIncome;

    /** 微信。 */
    @Excel(name = "微信", sort = 7, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal wechatAmount;

    /** 支付宝。 */
    @Excel(name = "支付宝", sort = 8, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal alipayAmount;

    /** 银行卡。 */
    @Excel(name = "银行卡", sort = 9, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal onlineBankAmount;

    /** 应交现金。 */
    @Excel(name = "应交现金", sort = 10, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal cashDueAmount;

    /** 药品合计金额。 */
    @Excel(name = "药品合计金额", sort = 11, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal medicationTotalAmount;

    /** 折扣合计。 */
    @Excel(name = "折扣合计", sort = 12, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal discountAmount;

    /** 应收。 */
    @Excel(name = "应收", sort = 13, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal receivableAmount;

    /** 医保统筹。 */
    @Excel(name = "医保统筹", sort = 14, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal insuranceCompensationAmount;

    /** 个人账户。 */
    @Excel(name = "个人账户", sort = 15, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal insuranceAccountAmount;
}
