/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.pharmacy.dispense.dto;

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
 * 就诊下药品发药单/发放行（患者发药信息）。
 *
 * <p>状态、类型等持久化语义字段对外以 {@code code} 字符串承载（与 {@code EnumUtil}、库表对齐，见代码生成通用规范 B.10.8.1）；展示列单独给出 {@code *Text}。</p>
 */
@Data
@Accessors(chain = true)
/** 就诊药品发药单行。 */
@Schema(description = "就诊药品发药单行")
public class EncounterMedicationDispenseSlipRowDto {

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 就诊业务流水号（展示）。 */
    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 药品发放聚合根主键。 */
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 关联的药品医嘱申请主键。 */
    @Schema(description = "药品申请主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;

    /** 药品定义（目录）主键。 */
    @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 本次发放对应的库房（库位）主键。 */
    @Schema(description = "发放库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseLocationId;

    /** 库房名称（展示）。 */
    @Schema(description = "发放库房名称")
    private String dispenseLocationName;

    /** 实物批次号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 药品追溯码（合并读模型与领域实体后的有效值）。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 开立科室对应的组织机构主键（常与医嘱执行科室一致）。 */
    @Schema(description = "开立科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingDepartmentId;

    /** 开立科室名称（展示）。 */
    @Schema(description = "开立科室名称")
    private String orderingDepartmentName;

    /** 开立医师医务主键。 */
    @Schema(description = "开单医生 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingPractitionerId;

    /** 开立医师姓名（展示）。 */
    @Schema(description = "开单医生姓名")
    private String orderingPractitionerName;

    /** 实际发药责任人姓名（展示）。 */
    @Schema(description = "发药药师姓名")
    private String dispensingPractitionerName;

    /** 药品大类术语码（目录 category）。 */
    @Schema(description = "药品大类术语码")
    private String medicationCategoryCode;

    /** 药品大类展示文案。 */
    @Schema(description = "药品大类展示")
    private String medicationCategoryText;

    /** 发放状态持久化 code（{@link com.openhis.health.domain.share.enums.DispenseStatus#getCode()}）。 */
    @Schema(description = "发放状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /** 发放状态展示名。 */
    @Schema(description = "发放状态展示")
    private String statusEnumText;

    /** 诊断摘要（如有）。 */
    @Schema(description = "诊断摘要")
    private String conditionSummaryText;

    /** 处方号/医嘱组关联展示号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 目录通用名/项目名称。 */
    @Schema(description = "项目名称")
    private String medicationName;

    /** 商品名（目录）。 */
    @Schema(description = "商品名")
    private String merchandiseName;

    /** 规格文本（如体积规格汇总）。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 生产厂家（药品主表 manufacturer 文本）。 */
    @Schema(description = "生产厂家")
    private String manufacturerName;

    /** 目录物理来源表锚点（与产品读模型一致）。 */
    @Schema(description = "目录物理表锚点")
    private String productTableName;

    /** 单次剂量数值（医嘱）。 */
    @Schema(description = "单次剂量")
    private BigDecimal doseValue;

    /** 拆零换算比（剂量单位相对拆零单位）。 */
    @Schema(description = "拆零比（目录剂量单位与拆零单位换算等，无则空）")
    private BigDecimal splitRatio;

    /** 用药频次术语码。 */
    @Schema(description = "用药频次码")
    private String frequencyCode;

    /** 用药频次展示（术语预热）。 */
    @Schema(description = "用药频次展示（术语解析）")
    private String frequencyText;

    /** 给药途径术语码。 */
    @Schema(description = "给药途径码")
    private String routeCode;

    /** 给药途径展示（术语预热）。 */
    @Schema(description = "给药途径展示（术语解析）")
    private String routeText;

    /** 剂量单位术语码。 */
    @Schema(description = "剂量单位码")
    private String doseUnitCode;

    /** 剂量单位展示（术语或目录投影）。 */
    @Schema(description = "剂量单位展示（术语或目录投影）")
    private String doseUnitText;

    /** 发放/计价所用业务单位术语码（与数量列一致）。 */
    @Schema(description = "发放/计价单位码（本发放行业务数量所用单位）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 发放单位展示（术语或目录折返）。 */
    @Schema(description = "发放/计价单位展示（术语解析）")
    private String unitText;

    /** 单次发药数量（展示辅助，可与 quantity 重叠业务口径）。 */
    @Schema(description = "单次发药数")
    private Integer dispensePerQuantity;

    /** 每次发药覆盖供应天数（医嘱）。 */
    @Schema(description = "每次发药供应天数")
    private Integer dispensePerSupplyDays;

    /** 医嘱开立总量（同一申请上的需求量）。 */
    @Schema(description = "医嘱开立总量（一次就诊同一药品申请上的总需求量，业务单位与医嘱一致）")
    private BigDecimal requestQuantity;

    /** 本发放分段数量（业务单位同 {@link #unitCode}）。 */
    @Schema(description = "本发放单行数量（对应本次发放分段的实物数量，单位同 unitCode）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 与本分段锚定的收费明细折算单价。 */
    @Schema(description = "单价（取自 adm_charge_item 与本发放分段锚点对应的收费明细，加权折算）")
    private BigDecimal unitPrice;

    /** 与本分段锚定的收费明细金额合计。 */
    @Schema(description = "金额（与本发放分段锚点对应的收费明细总价合计）")
    private BigDecimal totalPrice;

    /** 医嘱组标识（合理用药/组展示）。 */
    @Schema(description = "医嘱组号")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long prescriptionGroupId;

    /** 医嘱开立时刻。 */
    @Schema(description = "医嘱开具时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime requestAuthoredTime;

    /** 是否皮试医嘱（医嘱布尔）。 */
    @Schema(description = "是否皮试医嘱")
    private Boolean skinTestFlag;

    /** 皮试标志中文展示。 */
    @Schema(description = "皮试标志展示（是/否）")
    private String skinTestFlagText;

    /** 是否中医相关医嘱（医嘱布尔）。 */
    @Schema(description = "是否中医相关医嘱")
    private Boolean tcmFlag;

    /** 中药与西药中成药分类展示（非草药复方侧为 {@link com.openhis.health.domain.share.constants.MedicationCommonConstants#TCM_CLASS_DISPLAY_WESTERN_AND_PATENT}）。 */
    @Schema(description = "中药/西药中成药类展示文案")
    private String tcmFlagText;

    /** 是否输液/注射相关医嘱。 */
    @Schema(description = "是否输液/注射相关医嘱")
    private Boolean infusionFlag;

    /** 输液/注射相关展示。 */
    @Schema(description = "输液/注射相关展示（是/否）")
    private String infusionFlagText;

    /** 拆分属性持久化 code。 */
    @Schema(description = "拆分属性 code（目录或医嘱）")
    private String partAttributeCode;

    /** 拆分属性展示。 */
    @Schema(description = "拆分属性展示")
    private String partAttributeText;

    /** 就诊用药类别术语码（处方类别）。 */
    @Schema(description = "就诊用药类别码")
    private String encounterMedicationTypeCode;

    /** 就诊用药类别展示。 */
    @Schema(description = "就诊用药类别展示")
    private String encounterMedicationTypeText;

    /** 发放类型持久化 code（{@link com.openhis.health.domain.share.enums.MedicationDispenseType#getCode()}）。 */
    @Schema(description = "发放类型 code")
    private String dispenseEnum;

    /** 发放类型展示。 */
    @Schema(description = "发放类型展示")
    private String dispenseEnumText;

    /** 长临标识 therapy 持久化 code。 */
    @Schema(description = "长临标识 therapy code")
    private String therapyEnum;

    /** 长临医嘱展示名。 */
    @Schema(description = "长临医嘱展示")
    private String therapyEnumText;

    /** 发放业务流水号。 */
    @Schema(description = "发放业务号")
    private String busNo;

    /** 候选库房×批号可用量明细（工作台选批辅助）。 */
    @Schema(description = "候选库存明细")
    private List<MedicationDispenseInventoryDetailDto> inventoryDetailList;

    @Schema(description = "患者姓名（处方打印）")
    private String patientName;

    @Schema(description = "患者年龄展示（处方打印）")
    private String ageText;

    @Schema(description = "患者性别展示（处方打印）")
    private String genderEnumText;

    @Schema(description = "患者联系电话（处方打印）")
    private String patientPhone;

    @Schema(description = "医疗机构名称（处方打印页眉）")
    private String hospitalName;

    @Schema(description = "费用性质展示（处方打印）")
    private String feeNatureText;

    @Schema(description = "处方笺标题（处方打印）")
    private String prescriptionDocumentTitle;

    @Schema(description = "处方日期展示 YYYY-MM-DD（处方打印）")
    private String prescriptionDateText;

    @Schema(description = "处方类别展示行1（处方打印）")
    private String prescriptionTypeLine1;

    @Schema(description = "处方类别展示行2（处方打印）")
    private String prescriptionTypeLine2;

    @Schema(description = "处方药品金额合计（同处方号各行一致）")
    private BigDecimal prescriptionTotalPrice;

    @Schema(description = "处方药品金额展示（处方打印页脚）")
    private String prescriptionTotalPriceText;

    @Schema(description = "调配药师姓名（处方打印）")
    private String preparerPractitionerName;

    @Schema(description = "划价员姓名（处方打印）")
    private String pricingPractitionerName;

    @Schema(description = "药品主行左列展示（处方打印）")
    private String medicationDisplayLeft;

    @Schema(description = "药品主行右列展示（处方打印）")
    private String medicationDisplayRight;

    @Schema(description = "用法用量展示行（处方打印）")
    private String medicationUsageDisplayText;
}
