/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 修改药品医嘱草稿命令。
 */
@Getter
@Setter
public class ChangeMedicationOrderDraftCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 药品申请标识。 */
    private Long id;
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
    /** 单次剂量；非空时更新。 */
    private BigDecimal dose;
    /** 剂量单位编码；非空时更新。 */
    private String doseUnitCode;
    /** 用药天数；非空时更新。 */
    private Integer dispensePerDuration;
    /** 是否皮试；非空时更新。 */
    private Boolean skinTestFlag;
    /** 绑定诊断定义主键；非空时更新。 */
    private Long conditionDefinitionId;
    /** 就诊诊断主键；非空时更新。 */
    private Long encounterDiagnosisId;
    /** 费用性质/合同覆盖；非空时更新。 */
    private Long contractId;
    /** 药品成组号；非空时更新。 */
    private Long groupId;
    /** 首日量。 */
    private BigDecimal firstDose;
    /** 首日量单位编码。 */
    private String firstDoseUnitCode;

    /** 医生站列表展示排序号；非空时更新。 */
    private Integer sortNumber;
}
