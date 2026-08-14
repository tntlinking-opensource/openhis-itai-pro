/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-DOR-14：药品医嘱草稿行载荷（嵌于 {@link InpatientDoctorStationAdviceDraftLineRequest#getMedicationPayload()}）。
 *
 * <p>字段与统一列表 {@link InpatientDoctorStationAdviceRowDto} 药品列语义对齐，供暂存→领域
 * {@code CreateMedicationOrderDraftCommand} 装配。住院长临暂存均仅写 Request；费用与发放由护士执行落库。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站药品医嘱草稿行载荷")
public class SaveInpatientMedicationAdviceDraftLinePayload {

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

    /**
     * 输液滴速术语编码（{@code speed_code}；可空；落库 {@code med_medication_request.speed_code}）。
     */
    @Schema(description = "输液滴速编码")
    private String speedCode;

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

    /**
     * 首日/首次用量（仅长期药品；可空；BR-IP-ORD-030）。
     *
     * <p>空 = 首次执行槽仍用 {@link #quantity}；有值须 &gt;0，与 {@link #firstDoseUnitCode} 成对。
     * 临时/出院带药不得传值（域 BR-MRQ-010）。</p>
     */
    @Schema(description = "首日/首次用量（仅长期）")
    private BigDecimal firstDose;

    /** 首日/首次用量单位术语编码（与 {@link #firstDose} 成对）。 */
    @Schema(description = "首日用量单位编码")
    private String firstDoseUnitCode;

    /** 用药天数（与统一列表 dispensePerDuration 列回显一致）。 */
    @Schema(description = "用药天数")
    private Integer dispensePerDuration;

    /** 是否皮试（与统一列表 skinTestFlag 列回显一致）。 */
    @Schema(description = "是否皮试")
    private Boolean skinTestFlag;

    /**
     * 绑定就诊诊断主键有序串（落库 {@code med_medication_request.encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;

    /** 费用性质/合同覆盖，可空表示走患者默认。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质覆盖")
    private Long contractId;

    /**
     * 医嘱备注（落库 {@code med_medication_request.remark}；护士站随医嘱展示）。
     */
    @Schema(description = "医嘱备注")
    private String remark;
}
