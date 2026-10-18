/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

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
    /**
     * 输液滴速编码（术语 {@code speed_code}）；非空时更新（含空串清空）。
     */
    private String speedCode;
    /** 单次剂量；非空时更新。 */
    private BigDecimal dose;
    /** 剂量单位编码；非空时更新。 */
    private String doseUnitCode;
    /** 用药天数；非空时更新。 */
    private Integer dispensePerDuration;
    /** 是否皮试；非空时更新。 */
    private Boolean skinTestFlag;
    /**
     * 绑定就诊诊断主键有序串（列 {@code encounter_diagnosis_ids}）；非空时更新。
     */
    private String encounterDiagnosisIds;
    /** 费用性质/合同覆盖；非空时更新。 */
    private Long contractId;
    /** 药品成组号；非空时更新。 */
    private Long groupId;
    /**
     * 是否同步首日量：{@code true} 时用 {@link #firstDose}/{@link #firstDoseUnitCode} 覆盖实体
     * （二者均可为 null，表示清空；BR-IP-ORD-030 允许空=首次槽回落常规单次量）。
     * 未置 true 时不碰首日量（如中药仅改 therapy 的局部更新）。
     */
    private Boolean syncFirstDose;
    /**
     * 首日量；仅当 {@link #syncFirstDose}=true 时生效（可为 null 清空）。
     */
    private BigDecimal firstDose;
    /**
     * 首日量单位编码；仅当 {@link #syncFirstDose}=true 时生效（可为 null 清空）。
     */
    private String firstDoseUnitCode;
    /**
     * 是否同步分次用量序列：{@code true} 时用 {@link #doseSchedule}/{@link #quantitySchedule} 覆盖
     * （二者均可为 null/空，表示清空回落单剂量路径；BR-MRQ-035）。
     * 未置 true 时不碰 schedule（局部更新勿误清）。
     */
    private Boolean syncDoseQuantitySchedule;
    /**
     * 分次剂量序列；仅当 {@link #syncDoseQuantitySchedule}=true 时生效（可为 null 清空）。
     */
    private String doseSchedule;
    /**
     * 分次用量序列；仅当 {@link #syncDoseQuantitySchedule}=true 时生效（可为 null 清空）。
     */
    private String quantitySchedule;

    /** 医生站列表展示排序号；非空时更新。 */
    private Integer sortNumber;

    /**
     * 医嘱备注；非空时更新（含空串清空；与中药草稿 remark 口径一致）。
     */
    private String remark;
}
