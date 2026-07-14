/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-14：药品医嘱草稿行载荷。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站药品医嘱草稿行载荷")
public class SaveMedicationAdviceDraftLinePayload {

    /** {@code med_medication_definition} 主键，锚定开立药品。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键")
    private Long medicationDefinitionId;

    /** 开立数量（与 {@link #unitCode} 同语义）。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 开立单位术语编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 给药途径术语编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用药频次术语编码。 */
    @Schema(description = "频次编码")
    private String rateCode;

    /** 成组号；同号表示同组输液/配伍等。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 组号。 */
    @Schema(description = "组号")
    private Long groupId;

    /** 单次用量（与 doseUnitCode 同语义，与统一列表 dose / doseUnitCode 列回显一致）。 */
    @Schema(description = "单次用量（药品）")
    private BigDecimal dose;

    /** 单次用量单位术语编码。 */
    @Schema(description = "剂量单位编码")
    private String doseUnitCode;

    /** 用药天数（与统一列表 dispensePerDuration 列回显一致）。 */
    @Schema(description = "用药天数")
    private Integer dispensePerDuration;

    /** 是否皮试（与统一列表 skinTestFlag 列回显一致）。 */
    @Schema(description = "是否皮试")
    private Boolean skinTestFlag;

    /**
     * 目录 IF-DOR-11 已解析发药库位；IF-DOR-14 写路径定价 FIFO 直接在该库位取批号，
     * 不再二次 match 取药科室模板（不落库到 MedicationRequest，签发仍以 FIFO 发放为准）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药库位（目录已解析，暂存定价用）")
    private Long dispenseLocationId;

    /**
     * 诊断定义主键（落库 {@code med_medication_request.condition_definition_id}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义主键")
    private Long conditionDefinitionId;

    /**
     * 就诊诊断主键（落库 {@code med_medication_request.encounter_diagnosis_id}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊诊断主键")
    private Long encounterDiagnosisId;

    /** 费用性质/合同覆盖，可空表示走患者默认。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质覆盖")
    private Long contractId;
}
