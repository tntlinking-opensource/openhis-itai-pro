/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.prescriptionspotcheck.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门诊处方抽查 · 中药饮片导出行（对齐《中药饮片处方》表头，不含校验码/错误描述）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊处方抽查中药饮片行")
public class PrescriptionSpotCheckHerbalRowDto {

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

    /** 门（急）诊中医疾病诊断名称。 */
    @Excel(name = "门（急）诊中医疾病诊断名称（中医疾病诊断）", sort = 13)
    @Schema(description = "中医疾病诊断名称")
    private String tcmDiseaseName;

    /** 门（急）诊中医疾病诊断编码。 */
    @Excel(name = "门（急）诊中医疾病诊断编码（中医疾病诊断）", sort = 14)
    @Schema(description = "中医疾病诊断编码")
    private String tcmDiseaseCode;

    /** 门（急）诊中医证候诊断名称。 */
    @Excel(name = "门（急）诊中医证候诊断名称（中医证候诊断）", sort = 15)
    @Schema(description = "中医证候诊断名称")
    private String tcmSyndromeName;

    /** 门（急）诊中医证候诊断编码。 */
    @Excel(name = "门（急）诊中医证候诊断编码（中医证候诊断）", sort = 16)
    @Schema(description = "中医证候诊断编码")
    private String tcmSyndromeCode;

    /** 药品名称。 */
    @Excel(name = "药品名称", sort = 17)
    @Schema(description = "药品名称")
    private String drugName;

    /** 药品编码（目录 busNo）。 */
    @Excel(name = "药品编码", sort = 18)
    @Schema(description = "药品编码")
    private String drugBusNo;

    /** 药品医保信息业务编码（目录 ybNo）。 */
    @Excel(name = "药品医保信息业务编码", sort = 19)
    @Schema(description = "药品医保信息业务编码")
    private String drugYbNo;

    /** 剂数（chineseHerbsDoseQuantity）。 */
    @Excel(name = "剂数", sort = 20)
    @Schema(description = "剂数")
    private BigDecimal doseCount;

    /** 制备方法（与给药途径同取方头用法文案）。 */
    @Excel(name = "制备方法", sort = 21)
    @Schema(description = "制备方法")
    private String prepareMethodText;

    /** 给药途径（方头用法文案）。 */
    @Excel(name = "给药途径", sort = 22)
    @Schema(description = "给药途径")
    private String methodText;

    /** 每剂用量。 */
    @Excel(name = "每剂用量", sort = 23)
    @Schema(description = "每剂用量")
    private BigDecimal dosePerUnit;

    /** 每剂用量单位。 */
    @Excel(name = "每剂用量单位", sort = 24)
    @Schema(description = "每剂用量单位")
    private String doseUnitText;

    /** 脚注（特殊煎法 dosageInstruction 文案）。 */
    @Excel(name = "脚注", sort = 25)
    @Schema(description = "脚注")
    private String footnoteText;

    /** 药品顺序（同方内从 1 起的行序）。 */
    @Excel(name = "药品顺序", sort = 26, scale = 0)
    @Schema(description = "药品顺序")
    private Integer drugOrder;
}
