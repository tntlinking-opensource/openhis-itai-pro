/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 就诊下药品发药单/发放行（门诊 IF-OPP-03 / 住院 IF-IPP-03 共用读模型）。
 *
 * <p>状态、类型等持久化语义字段对外以 {@code code} 字符串承载；展示列单独给出 {@code *Text}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "就诊药品发药单行")
public class EncounterMedicationDispenseSlipRowDto {

    /** 就诊主键 {@code adm_encounter.id}。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 就诊业务号。 */
    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 药品发放单主键 {@code med_medication_dispense.id}。 */
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 药品申请主键 {@code med_medication_request.id}。 */
    @Schema(description = "药品申请主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 发放库房/货位主键。 */
    @Schema(description = "发放库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseLocationId;

    /** 发放库房展示名。 */
    @Schema(description = "发放库房名称")
    private String dispenseLocationName;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 开立科室主键。 */
    @Schema(description = "开立科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingDepartmentId;

    /** 开立科室展示名。 */
    @Schema(description = "开立科室名称")
    private String orderingDepartmentName;

    /** 开单医生主键。 */
    @Schema(description = "开单医生 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingPractitionerId;

    /** 开单医生姓名。 */
    @Schema(description = "开单医生姓名")
    private String orderingPractitionerName;

    /** 发药药师姓名。 */
    @Schema(description = "发药药师姓名")
    private String dispensingPractitionerName;

    /** 药品大类术语码。 */
    @Schema(description = "药品大类术语码")
    private String medicationCategoryCode;

    /** 药品大类展示名。 */
    @Schema(description = "药品大类展示")
    private String medicationCategoryText;

    /** 发放状态枚举码。 */
    @Schema(description = "发放状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /** 发放状态展示名。 */
    @Schema(description = "发放状态展示")
    private String statusEnumText;

    /** 诊断摘要（快照拼接）。 */
    @Schema(description = "诊断摘要")
    private String conditionSummaryText;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 药品/项目名称。 */
    @Schema(description = "项目名称")
    private String medicationName;

    /** 商品名。 */
    @Schema(description = "商品名")
    private String merchandiseName;

    /** 规格展示文本。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 生产厂家名称。 */
    @Schema(description = "生产厂家")
    private String manufacturerName;

    /** 产品定义来源表名锚点。 */
    @Schema(description = "目录物理表锚点")
    private String productTableName;

    /** 单次剂量数值。 */
    @Schema(description = "单次剂量")
    private BigDecimal doseValue;

    /** 拆零换算比。 */
    @Schema(description = "拆零比")
    private BigDecimal splitRatio;

    /** 用药频次码。 */
    @Schema(description = "用药频次码")
    private String frequencyCode;

    /** 用药频次展示名。 */
    @Schema(description = "用药频次展示")
    private String frequencyText;

    /** 给药途径码。 */
    @Schema(description = "给药途径码")
    private String routeCode;

    /** 给药途径展示名。 */
    @Schema(description = "给药途径展示")
    private String routeText;

    /** 剂量单位码。 */
    @Schema(description = "剂量单位码")
    private String doseUnitCode;

    /** 剂量单位展示名。 */
    @Schema(description = "剂量单位展示")
    private String doseUnitText;

    /** 发放/计价单位码。 */
    @Schema(description = "发放/计价单位码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 发放/计价单位展示名。 */
    @Schema(description = "发放/计价单位展示")
    private String unitText;

    /** 单次发药数量。 */
    @Schema(description = "单次发药数")
    private Integer dispensePerQuantity;

    /** 每次发药供应天数。 */
    @Schema(description = "每次发药供应天数")
    private Integer dispensePerSupplyDays;

    /** 医嘱开立总量。 */
    @Schema(description = "医嘱开立总量")
    private BigDecimal requestQuantity;

    /** 本发放单行数量。 */
    @Schema(description = "本发放单行数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 金额（单价×数量）。 */
    @Schema(description = "金额")
    private BigDecimal totalPrice;

    /** 医嘱组号/处方组主键。 */
    @Schema(description = "医嘱组号")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long prescriptionGroupId;

    /** 医嘱开具时间。 */
    @Schema(description = "医嘱开具时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime requestAuthoredTime;

    /** 是否皮试医嘱。 */
    @Schema(description = "是否皮试医嘱")
    private Boolean skinTestFlag;

    /** 皮试标志展示文本。 */
    @Schema(description = "皮试标志展示")
    private String skinTestFlagText;

    /** 是否中医相关医嘱。 */
    @Schema(description = "是否中医相关医嘱")
    private Boolean tcmFlag;

    /** 中药/西药展示文本。 */
    @Schema(description = "中药/西药展示")
    private String tcmFlagText;

    /** 是否输液/注射相关医嘱。 */
    @Schema(description = "是否输液/注射相关医嘱")
    private Boolean infusionFlag;

    /** 输液/注射展示文本。 */
    @Schema(description = "输液/注射展示")
    private String infusionFlagText;

    /** 拆分属性枚举码。 */
    @Schema(description = "拆分属性 code")
    private String partAttributeCode;

    /** 拆分属性展示名。 */
    @Schema(description = "拆分属性展示")
    private String partAttributeText;

    /** 就诊用药类别码。 */
    @Schema(description = "就诊用药类别码")
    private String encounterMedicationTypeCode;

    /** 就诊用药类别展示名。 */
    @Schema(description = "就诊用药类别展示")
    private String encounterMedicationTypeText;

    /** 发放类型枚举码。 */
    @Schema(description = "发放类型 code")
    private String dispenseEnum;

    /** 发放类型展示名。 */
    @Schema(description = "发放类型展示")
    private String dispenseEnumText;

    /** 长临标识 therapy 枚举码。 */
    @Schema(description = "长临标识 therapy code")
    private String therapyEnum;

    /** 长临医嘱展示名。 */
    @Schema(description = "长临医嘱展示")
    private String therapyEnumText;

    /** 发放业务号。 */
    @Schema(description = "发放业务号")
    private String busNo;

    /** 候选库存明细（门诊工作台选批号用）。 */
    @Schema(description = "候选库存明细（门诊工作台）")
    private List<MedicationDispenseInventoryDetailDto> inventoryDetailList;

    /** 患者姓名（处方打印）。 */
    @Schema(description = "患者姓名（处方打印）")
    private String patientName;

    /** 患者年龄展示（处方打印）。 */
    @Schema(description = "患者年龄展示（处方打印）")
    private String ageText;

    /** 患者性别展示（处方打印）。 */
    @Schema(description = "患者性别展示（处方打印）")
    private String genderEnumText;

    /** 患者联系电话（处方打印）。 */
    @Schema(description = "患者联系电话（处方打印）")
    private String patientPhone;

    /** 医疗机构名称（处方打印页眉）。 */
    @Schema(description = "医疗机构名称（处方打印页眉）")
    private String hospitalName;

    /** 费用性质展示（处方打印）。 */
    @Schema(description = "费用性质展示（处方打印）")
    private String feeNatureText;

    /** 处方笺标题（处方打印）。 */
    @Schema(description = "处方笺标题（处方打印）")
    private String prescriptionDocumentTitle;

    /** 处方日期展示 YYYY-MM-DD（处方打印）。 */
    @Schema(description = "处方日期展示 YYYY-MM-DD（处方打印）")
    private String prescriptionDateText;

    /** 处方类别展示行1（处方打印）。 */
    @Schema(description = "处方类别展示行1（处方打印）")
    private String prescriptionTypeLine1;

    /** 处方类别展示行2（处方打印）。 */
    @Schema(description = "处方类别展示行2（处方打印）")
    private String prescriptionTypeLine2;

    /** 处方药品金额合计（同处方号各行一致）。 */
    @Schema(description = "处方药品金额合计（同处方号各行一致）")
    private BigDecimal prescriptionTotalPrice;

    /** 处方药品金额展示（处方打印页脚）。 */
    @Schema(description = "处方药品金额展示（处方打印页脚）")
    private String prescriptionTotalPriceText;

    /** 调配药师姓名（处方打印）。 */
    @Schema(description = "调配药师姓名（处方打印）")
    private String preparerPractitionerName;

    /** 划价员姓名（处方打印）。 */
    @Schema(description = "划价员姓名（处方打印）")
    private String pricingPractitionerName;

    /** 药品主行左列展示（处方打印）。 */
    @Schema(description = "药品主行左列展示（处方打印）")
    private String medicationDisplayLeft;

    /** 药品主行右列展示（处方打印）。 */
    @Schema(description = "药品主行右列展示（处方打印）")
    private String medicationDisplayRight;

    /** 用法用量展示行（处方打印）。 */
    @Schema(description = "用法用量展示行（处方打印）")
    private String medicationUsageDisplayText;

    /** 单味特殊煎法术语码（dosage_instruction）。 */
    @Schema(description = "特殊煎法术语码")
    private String dosageInstructionCode;

    /** 单味特殊煎法展示名。 */
    @Schema(description = "特殊煎法展示名")
    private String dosageInstructionText;
}
