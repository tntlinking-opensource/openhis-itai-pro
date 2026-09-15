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

    /**
     * 挂接的护士执行记录主键 {@code cln_procedure.id}。
     * <p>住院 IF-IPP-03 按日虚拟发药单用；无执行挂接时为空。</p>
     */
    @Schema(description = "执行记录主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long procedureId;

    /**
     * 护士认定实际执行时刻（{@code cln_procedure.recorded_time}）。
     * <p>住院按患者发药前端按本字段自然日虚拟分「发药单」；与汇总领药 planned 轴无关。</p>
     */
    @Schema(description = "护士执行 recorded_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime recordedTime;

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

    /**
     * 包装最小单位换算比展示（服务端拼装），形如 {@code 1盒/36片}。
     *
     * <p>与库存明细 / 门诊发药 slip 同族：1 包装 = ratio 拆零；同单位×1 为 {@code 1支}。
     * 非临床装量后缀 {@code 1片x100}。</p>
     */
    @Schema(description = "包装最小单位换算比展示，如 1盒/36片")
    private String packageSplitRatioText;

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

    /**
     * 包装单位码（产品定义 {@code package_unit_code}）。
     * <p>住院发药待扫换算：操作量换拆零时用。</p>
     */
    @Schema(description = "包装单位码")
    private String packageUnitCode;

    /**
     * 包装相对拆零倍数（1 包装 = N 拆零）。
     */
    @Schema(description = "包装相对拆零倍数")
    private BigDecimal packageUnitRatio;

    /**
     * 拆零单位码（产品定义 {@code split_unit_code}）。
     */
    @Schema(description = "拆零单位码")
    private String splitUnitCode;

    /**
     * 追溯码贴码单位（优先主档；无主档时回落药典目录）。
     *
     * <p>发药应扫个数换算与扫码弹窗展示用。</p>
     */
    @Schema(description = "追溯码贴码单位（主档优先，否则药典）")
    private String traceUnitCode;

    /**
     * 追溯码贴码单位展示文案（{@code UNIT_CODE} 术语；扫码弹窗药品明细列用）。
     */
    @Schema(description = "追溯码贴码单位展示文案")
    private String traceUnitCodeText;

    /**
     * 追溯码贴码比例（优先主档；无主档时回落药典；1 码单位 = N 拆零）。
     *
     * <p>发药应扫个数 = ceil(行拆零 / 本值)。</p>
     */
    @Schema(description = "追溯码贴码比例（1码=N拆零；主档优先否则药典）")
    private BigDecimal traceUnitRatio;

    /**
     * 主表该品批是否已有追溯码（有码批=true）。
     *
     * <p>false=无码批：扫码仅软提示，发药只写业务行不写主表流水；true=有码批：消耗侧码身份软提示（仅进货硬拦），写流水盖不住跳过。</p>
     */
    @Schema(description = "主表该品批是否已有追溯码")
    private Boolean lotHasTraceMaster;

    /** 单次发药数量。 */
    @Schema(description = "单次发药数")
    private Integer dispensePerQuantity;

    /** 每次发药供应天数。 */
    @Schema(description = "每次发药供应天数")
    private Integer dispensePerSupplyDays;

    /** 医嘱开立总量。 */
    @Schema(description = "医嘱开立总量")
    private BigDecimal requestQuantity;

    /**
     * 中药付数（方头 {@code chinese_herbs_dose_quantity}）。
     *
     * <p>处方笺「N付」须用本字段；勿用 {@link #requestQuantity}（单剂×付数的请求总量）。</p>
     */
    @Schema(description = "中药付数")
    private BigDecimal chineseHerbsDoseQuantity;

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

    /** 医嘱开立时间（{@code req_opened_time}，空回落 {@code req_authored_time}）。 */
    @Schema(description = "医嘱开立时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime requestOpenedTime;

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

    /**
     * 发放库房×批号当前剩余数量（最小单位，现查）。
     * <p>住院不返回候选库存列表；仅锚定行上库房批号账面剩余供笺面「剩余库存」回显。</p>
     */
    @Schema(description = "发放库房×批号当前剩余数量（最小单位）")
    private BigDecimal remainingQuantity;

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
