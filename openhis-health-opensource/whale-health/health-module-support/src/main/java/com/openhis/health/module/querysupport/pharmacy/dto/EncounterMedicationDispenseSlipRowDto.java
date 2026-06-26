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

package com.openhis.health.module.querysupport.pharmacy.dto;

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

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    @Schema(description = "药品申请主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;

    @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    @Schema(description = "发放库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseLocationId;

    @Schema(description = "发放库房名称")
    private String dispenseLocationName;

    @Schema(description = "批号")
    private String lotNumber;

    @Schema(description = "追溯码")
    private String traceNo;

    @Schema(description = "开立科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingDepartmentId;

    @Schema(description = "开立科室名称")
    private String orderingDepartmentName;

    @Schema(description = "开单医生 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingPractitionerId;

    @Schema(description = "开单医生姓名")
    private String orderingPractitionerName;

    @Schema(description = "发药药师姓名")
    private String dispensingPractitionerName;

    @Schema(description = "药品大类术语码")
    private String medicationCategoryCode;

    @Schema(description = "药品大类展示")
    private String medicationCategoryText;

    @Schema(description = "发放状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    @Schema(description = "发放状态展示")
    private String statusEnumText;

    @Schema(description = "诊断摘要")
    private String conditionSummaryText;

    @Schema(description = "处方号")
    private String prescriptionNo;

    @Schema(description = "项目名称")
    private String medicationName;

    @Schema(description = "商品名")
    private String merchandiseName;

    @Schema(description = "规格")
    private String specificationText;

    @Schema(description = "生产厂家")
    private String manufacturerName;

    @Schema(description = "目录物理表锚点")
    private String productTableName;

    @Schema(description = "单次剂量")
    private BigDecimal doseValue;

    @Schema(description = "拆零比")
    private BigDecimal splitRatio;

    @Schema(description = "用药频次码")
    private String frequencyCode;

    @Schema(description = "用药频次展示")
    private String frequencyText;

    @Schema(description = "给药途径码")
    private String routeCode;

    @Schema(description = "给药途径展示")
    private String routeText;

    @Schema(description = "剂量单位码")
    private String doseUnitCode;

    @Schema(description = "剂量单位展示")
    private String doseUnitText;

    @Schema(description = "发放/计价单位码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    @Schema(description = "发放/计价单位展示")
    private String unitText;

    @Schema(description = "单次发药数")
    private Integer dispensePerQuantity;

    @Schema(description = "每次发药供应天数")
    private Integer dispensePerSupplyDays;

    @Schema(description = "医嘱开立总量")
    private BigDecimal requestQuantity;

    @Schema(description = "本发放单行数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "金额")
    private BigDecimal totalPrice;

    @Schema(description = "医嘱组号")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long prescriptionGroupId;

    @Schema(description = "医嘱开具时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime requestAuthoredTime;

    @Schema(description = "是否皮试医嘱")
    private Boolean skinTestFlag;

    @Schema(description = "皮试标志展示")
    private String skinTestFlagText;

    @Schema(description = "是否中医相关医嘱")
    private Boolean tcmFlag;

    @Schema(description = "中药/西药展示")
    private String tcmFlagText;

    @Schema(description = "是否输液/注射相关医嘱")
    private Boolean infusionFlag;

    @Schema(description = "输液/注射展示")
    private String infusionFlagText;

    @Schema(description = "拆分属性 code")
    private String partAttributeCode;

    @Schema(description = "拆分属性展示")
    private String partAttributeText;

    @Schema(description = "就诊用药类别码")
    private String encounterMedicationTypeCode;

    @Schema(description = "就诊用药类别展示")
    private String encounterMedicationTypeText;

    @Schema(description = "发放类型 code")
    private String dispenseEnum;

    @Schema(description = "发放类型展示")
    private String dispenseEnumText;

    @Schema(description = "长临标识 therapy code")
    private String therapyEnum;

    @Schema(description = "长临医嘱展示")
    private String therapyEnumText;

    @Schema(description = "发放业务号")
    private String busNo;

    @Schema(description = "候选库存明细（门诊工作台）")
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
