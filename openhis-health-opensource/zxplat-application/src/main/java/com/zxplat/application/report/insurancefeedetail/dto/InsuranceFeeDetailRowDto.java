/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.insurancefeedetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医保费用明细报表行（AS-RPT-012/013；无处方号）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保费用明细报表行")
public class InsuranceFeeDetailRowDto {

    /** 结算回执主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "结算回执主键")
    private Long settlementRecordId;

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 患者姓名。 */
    @Excel(name = "患者姓名", sort = 1)
    @Schema(description = "患者姓名")
    private String patientName;

    /**
     * 就诊科室名（门诊=挂号科 / 住院=入院科，来自 {@code Encounter.organization_id}）。
     */
    @Excel(name = "科室", sort = 2)
    @Schema(description = "就诊科室名称")
    private String organizationName;

    /**
     * 门诊号或住院号（业务号）；Excel 默认标题为「业务号」，导出走前端 exportColumns 覆盖。
     */
    @Excel(name = "业务号", sort = 3)
    @Schema(description = "门诊号/住院号")
    private String busNo;

    /** 就医登记号。 */
    @Excel(name = "就医登记号", sort = 4)
    @Schema(description = "就医登记号")
    private String mdtrtId;

    /** 结算 ID。 */
    @Excel(name = "结算ID", sort = 5)
    @Schema(description = "结算ID")
    private String setlId;

    /** 结算时间。 */
    @Excel(name = "结算时间", sort = 6)
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "结算时间")
    private LocalDateTime setlTime;

    /** 医疗费总额（结算 2 位展示）。 */
    @Excel(name = "医疗费总额", sort = 7, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "医疗费总额")
    private BigDecimal medfeeSumamt;

    /** 基金支付总额。 */
    @Excel(name = "基金支付总额", sort = 8, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "基金支付总额")
    private BigDecimal fundPaySumamt;

    /** 个人账户支付。 */
    @Excel(name = "个人账户支付", sort = 9, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "个人账户支付")
    private BigDecimal acctPay;

    /** 现金支付。 */
    @Excel(name = "现金支付", sort = 10, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "现金支付")
    private BigDecimal psnCashPay;
}
