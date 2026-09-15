/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto;

import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 点评记录导出行（IF-PRV-03；@Excel 白名单与「处方点评工作表」数据列对齐，无序号）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "点评记录导出行")
public class PrescriptionReviewExportRowDto {

    /** 处方编号。 */
    @Excel(name = "处方编号", sort = 1)
    @Schema(description = "处方编号")
    private String prescriptionNo;

    /** 年龄(岁)。 */
    @Excel(name = "年龄(岁)", sort = 2)
    @Schema(description = "年龄(岁)")
    private String patientAgeText;

    /** 诊断。 */
    @Excel(name = "诊断", sort = 3)
    @Schema(description = "诊断")
    private String diagnosisName;

    /** 药品品种。 */
    @Excel(name = "药品品种", sort = 4)
    @Schema(description = "药品品种")
    private Integer drugVarietyCount;

    /** 抗菌药(0/1)。 */
    @Excel(name = "抗菌药(0/1)", sort = 5)
    @Schema(description = "抗菌药(0/1)")
    private Integer antibioticFlag;

    /** 注射剂(0/1)。 */
    @Excel(name = "注射剂(0/1)", sort = 6)
    @Schema(description = "注射剂(0/1)")
    private Integer injectFlag;

    /** 国家基本药物品种数。 */
    @Excel(name = "国家基本药物品种数", sort = 7)
    @Schema(description = "国家基本药物品种数")
    private Integer basicDrugVarietyCount;

    /** 药品通用名数。 */
    @Excel(name = "药品通用名数", sort = 8)
    @Schema(description = "药品通用名数")
    private Integer genericNameCount;

    /** 处方金额(元)。 */
    @Excel(name = "处方金额(元)", sort = 9, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "处方金额(元)")
    private BigDecimal prescriptionAmount;

    /** 处方医师。 */
    @Excel(name = "处方医师", sort = 10)
    @Schema(description = "处方医师")
    private String orderingPractitionerName;

    /** 是否合理(0/1)。 */
    @Excel(name = "是否合理(0/1)", sort = 11)
    @Schema(description = "是否合理(0/1)")
    private Integer reasonableFlag;

    /** 存在问题（字段名 reason）。 */
    @Excel(name = "存在问题", sort = 12)
    @Schema(description = "存在问题")
    private String reason;
}
