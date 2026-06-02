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

package com.openhis.health.application.inpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院就诊下药品发药单/发放行（IF-IPP-03）。
 *
 * <p>库房、批号、数量为执行侧写入的只读快照；不含候选库存列表。</p>
 * <p>状态、类型等持久化语义字段对外以 {@code code} 字符串承载（与 {@code EnumUtil}、库表对齐，见代码生成通用规范 B.10.8.1）；展示列单独给出 {@code *Text}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院药品发药单行")
public class EncounterMedicationDispenseSlipRowDto {

    /** 住院就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院号（展示）。 */
    @Schema(description = "住院号")
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

    /** 本次发放对应的库房（库位）主键（只读，执行侧写入）。 */
    @Schema(description = "发放库房主键（只读）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseLocationId;

    /** 库房名称（展示）。 */
    @Schema(description = "发放库房名称")
    private String dispenseLocationName;

    /** 实物批次号（只读，执行侧写入）。 */
    @Schema(description = "批号（只读）")
    private String lotNumber;

    /** 药品追溯码（合并读模型与领域实体后的有效值）。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 开立科室对应的组织机构主键。 */
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

    /** 规格文本。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 生产厂家。 */
    @Schema(description = "生产厂家")
    private String manufacturerName;

    /** 目录物理来源表锚点（与产品读模型一致）。 */
    @Schema(description = "目录物理表锚点")
    private String productTableName;

    /** 单次剂量数值（医嘱）。 */
    @Schema(description = "单次剂量")
    private BigDecimal doseValue;

    /** 拆零换算比（剂量单位相对拆零单位）。 */
    @Schema(description = "拆零比")
    private BigDecimal splitRatio;

    /** 用药频次术语码。 */
    @Schema(description = "用药频次码")
    private String frequencyCode;

    /** 用药频次展示（术语预热）。 */
    @Schema(description = "用药频次展示")
    private String frequencyText;

    /** 给药途径术语码。 */
    @Schema(description = "给药途径码")
    private String routeCode;

    /** 给药途径展示（术语预热）。 */
    @Schema(description = "给药途径展示")
    private String routeText;

    /** 剂量单位术语码。 */
    @Schema(description = "剂量单位码")
    private String doseUnitCode;

    /** 剂量单位展示。 */
    @Schema(description = "剂量单位展示")
    private String doseUnitText;

    /** 发放/计价所用业务单位术语码（与数量列一致）。 */
    @Schema(description = "发放单位码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 发放单位展示。 */
    @Schema(description = "发放单位展示")
    private String unitText;

    /** 单次发药数量（展示辅助）。 */
    @Schema(description = "单次发药数")
    private Integer dispensePerQuantity;

    /** 每次发药覆盖供应天数（医嘱）。 */
    @Schema(description = "每次发药供应天数")
    private Integer dispensePerSupplyDays;

    /** 医嘱开立总量。 */
    @Schema(description = "医嘱开立总量")
    private BigDecimal requestQuantity;

    /** 本发放分段数量（只读，业务单位同 {@link #unitCode}）。 */
    @Schema(description = "本行发放数量（只读）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 单价（可选展示）。 */
    @Schema(description = "单价（可选展示）")
    private BigDecimal unitPrice;

    /** 金额（可选展示）。 */
    @Schema(description = "金额（可选展示）")
    private BigDecimal totalPrice;

    /** 医嘱组标识。 */
    @Schema(description = "医嘱组号")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long prescriptionGroupId;

    /** 医嘱开立时刻。 */
    @Schema(description = "医嘱开具时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestAuthoredTime;

    /** 是否皮试医嘱。 */
    @Schema(description = "是否皮试医嘱")
    private Boolean skinTestFlag;

    /** 皮试标志中文展示。 */
    @Schema(description = "皮试标志展示")
    private String skinTestFlagText;

    /** 是否中医相关医嘱。 */
    @Schema(description = "是否中医相关医嘱")
    private Boolean tcmFlag;

    /** 中药/西药展示文案。 */
    @Schema(description = "中药/西药展示")
    private String tcmFlagText;

    /** 是否输液/注射相关医嘱。 */
    @Schema(description = "是否输液/注射相关医嘱")
    private Boolean infusionFlag;

    /** 输液/注射相关展示。 */
    @Schema(description = "输液/注射展示")
    private String infusionFlagText;

    /** 拆分属性持久化 code。 */
    @Schema(description = "拆分属性 code")
    private String partAttributeCode;

    /** 拆分属性展示。 */
    @Schema(description = "拆分属性展示")
    private String partAttributeText;

    /** 就诊用药类别术语码。 */
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

    /** 长临标识 therapy 持久化 code（{@link com.openhis.health.domain.share.enums.TherapyType#getCode()}）。 */
    @Schema(description = "长临标识 code")
    private String therapyEnum;

    /** 长临医嘱展示名。 */
    @Schema(description = "长临医嘱展示")
    private String therapyEnumText;

    /** 发放业务流水号。 */
    @Schema(description = "发放业务号")
    private String busNo;
}
