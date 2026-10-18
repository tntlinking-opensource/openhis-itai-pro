/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.prescriptionspotcheck.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门诊处方抽查 · 西药及中成药导出行（对齐《西药及中成药处方》表头，无校验码）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊处方抽查西药及中成药行")
public class PrescriptionSpotCheckWesternRowDto {

    /** 医疗机构名称。 */
    @Excel(name = "医疗机构名称", sort = 1)
    @Schema(description = "医疗机构名称")
    private String orgName;

    /** 医疗机构执业许可证登记号（医院 Organization.yb_no）。 */
    @Excel(name = "医疗机构执业许可证登记号", sort = 2)
    @Schema(description = "医疗机构执业许可证登记号")
    private String orgLicenseNo;

    /** 处方编号。 */
    @Excel(name = "处方编号", sort = 3)
    @Schema(description = "处方编号")
    private String prescriptionNo;

    /** 处方金额（元，结算 2 位）。 */
    @Excel(name = "处方金额（元）", sort = 4, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "处方金额（元）")
    private BigDecimal prescriptionAmount;

    /** 患者唯一标识（patient.busNo）。 */
    @Excel(name = "患者唯一标识", sort = 5)
    @Schema(description = "患者唯一标识")
    private String patientBusNo;

    /** 性别短文案（男/女）。 */
    @Excel(name = "性别", sort = 6)
    @Schema(description = "性别")
    private String genderText;

    /** 出生日期。 */
    @Excel(name = "出生日期", sort = 7)
    @JsonFormat(pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /** 处方开立日期时刻。 */
    @Excel(name = "处方开立日期", sort = 8)
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "处方开立日期")
    private LocalDateTime openedAt;

    /** 患者就诊类型（固定门诊）。 */
    @Excel(name = "患者就诊类型", sort = 9)
    @Schema(description = "患者就诊类型")
    private String visitTypeText;

    /** 处方开立科室名称。 */
    @Excel(name = "处方开立科室名称", sort = 10)
    @Schema(description = "处方开立科室名称")
    private String deptName;

    /** 处方开立科室代码（Organization.yb_no）。 */
    @Excel(name = "处方开立科室代码", sort = 11)
    @Schema(description = "处方开立科室代码")
    private String deptYbNo;

    /** 处方开立医师代码（Practitioner.yb_no）。 */
    @Excel(name = "处方开立医师代码", sort = 12)
    @Schema(description = "处方开立医师代码")
    private String practitionerYbNo;

    /** 西医诊断名称（顿号拼接）。 */
    @Excel(name = "诊断名称", sort = 13)
    @Schema(description = "诊断名称")
    private String diagnosisName;

    /** 西医诊断编码（顿号拼接）。 */
    @Excel(name = "诊断编码", sort = 14)
    @Schema(description = "诊断编码")
    private String diagnosisCode;

    /** 药品编码（目录 busNo）。 */
    @Excel(name = "药品编码", sort = 15)
    @Schema(description = "药品编码")
    private String drugBusNo;

    /** 药品医保信息业务编码（目录 ybNo）。 */
    @Excel(name = "药品医保信息业务编码", sort = 16)
    @Schema(description = "药品医保信息业务编码")
    private String drugYbNo;

    /** 药品名称（医保名）。 */
    @Excel(name = "药品名称（医保名）", sort = 17)
    @Schema(description = "药品名称（医保名）")
    private String drugYbName;

    /** 剂型文案。 */
    @Excel(name = "剂型", sort = 18)
    @Schema(description = "剂型")
    private String doseFormText;

    /** 规格（目录 totalVolume）。 */
    @Excel(name = "规格", sort = 19)
    @Schema(description = "规格")
    private String specification;

    /** 包装（包装/拆零比例文案）。 */
    @Excel(name = "包装", sort = 20)
    @Schema(description = "包装")
    private String packagingText;

    /** 给药途径。 */
    @Excel(name = "给药途径", sort = 21)
    @Schema(description = "给药途径")
    private String methodText;

    /** 频次。 */
    @Excel(name = "频次", sort = 22)
    @Schema(description = "频次")
    private String rateText;

    /** 单次剂量。 */
    @Excel(name = "单次剂量", sort = 23)
    @Schema(description = "单次剂量")
    private BigDecimal doseQuantity;

    /** 单次剂量单位。 */
    @Excel(name = "单次剂量单位", sort = 24)
    @Schema(description = "单次剂量单位")
    private String doseUnitText;

    /** 发药数量。 */
    @Excel(name = "发药数量", sort = 25)
    @Schema(description = "发药数量")
    private BigDecimal dispenseQuantity;

    /** 发药数量单位。 */
    @Excel(name = "发药数量单位", sort = 26)
    @Schema(description = "发药数量单位")
    private String dispenseUnitText;

    /** 药品顺序（同方内从 1 起的行序）。 */
    @Excel(name = "药品顺序", sort = 27, scale = 0)
    @Schema(description = "药品顺序")
    private Integer drugOrder;
}
