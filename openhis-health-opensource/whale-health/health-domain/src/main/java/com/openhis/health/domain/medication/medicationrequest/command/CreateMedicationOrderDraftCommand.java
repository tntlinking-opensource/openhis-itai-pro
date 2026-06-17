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

package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建药品医嘱草稿命令。
 */
@Getter
@Setter
public class CreateMedicationOrderDraftCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 患者标识。 */
    private Long patientId;
    /** 药品定义标识。 */
    private Long medicationId;
    /** 治疗类型编码。 */
    private String therapyEnum;
    /** 数量。 */
    private BigDecimal quantity;
    /** 数量单位编码。 */
    private String unitCode;
    /** 用法编码。 */
    private String methodCode;
    /** 频次编码。 */
    private String rateCode;
    /** 单次剂量。 */
    private BigDecimal dose;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 是否皮试。 */
    private Boolean skinTestFlag;
    /**
     * 绑定诊断定义主键（医生站 {@code medicationPayload.conditionDefinitionId}，列 {@code condition_definition_id}）。
     */
    private Long conditionDefinitionId;
    /**
     * 就诊诊断主键（医生站 {@code medicationPayload.encounterDiagnosisId}，列 {@code encounter_diagnosis_id}）。
     */
    private Long encounterDiagnosisId;
    /** 费用性质/合同覆盖（列 {@code contract_id}；可空表示走患者默认）。 */
    private Long contractId;
    /** 药品成组号。 */
    private Long groupId;
    /** 首日量。 */
    private BigDecimal firstDose;
    /** 首日量单位编码。 */
    private String firstDoseUnitCode;

    /**
     * 生成来源持久化码（医生站开立与列表过滤一致；可空表示由调用方未显式传入）。
     */
    private String generateSourceEnum;

    /** 开立机构主键（门诊接诊/开立科室）。 */
    private Long orgId;

    /** 记录人主键（开立医师）。 */
    private Long recorderId;

    /** 医生站列表展示排序号（可选）。 */
    private Integer sortNumber;
}

