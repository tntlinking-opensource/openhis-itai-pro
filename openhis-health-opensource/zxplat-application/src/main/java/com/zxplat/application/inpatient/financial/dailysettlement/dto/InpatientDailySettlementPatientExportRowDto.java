/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.dailysettlement.dto;

import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院日结患者分项导出行（对齐门诊费用明细：{@code @Excel} + AnnotatedExcelExporter）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结患者分项导出行")
public class InpatientDailySettlementPatientExportRowDto {

    /** 分块序号。 */
    @Excel(name = "序号", sort = 1)
    private Integer seqNo;

    /** 患者姓名。 */
    @Excel(name = "患者姓名", sort = 2)
    private String patientName;

    /** 住院号。 */
    @Excel(name = "住院号", sort = 3)
    private String encounterBusNo;

    /** 病区。 */
    @Excel(name = "病区", sort = 4)
    private String wardName;

    /** 床位。 */
    @Excel(name = "床位", sort = 5)
    private String bedName;

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

    /** 预交金。 */
    @Excel(name = "预交金", sort = 16, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal depositAmount;

    /** 退预交金。 */
    @Excel(name = "退预交金", sort = 17, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal depositRefundAmount;

    /** 结算补收。 */
    @Excel(name = "结算补收", sort = 18, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal settleSupplementAmount;
}
