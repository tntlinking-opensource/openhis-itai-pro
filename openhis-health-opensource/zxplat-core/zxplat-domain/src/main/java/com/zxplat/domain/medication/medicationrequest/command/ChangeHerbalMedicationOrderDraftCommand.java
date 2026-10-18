/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 修改中药医嘱草稿命令（null 字段表示不变更，与 {@link ChangeMedicationOrderDraftCommand} 分轨）。
 */
@Getter
@Setter
public class ChangeHerbalMedicationOrderDraftCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 药品申请标识。 */
    private Long id;
    /** 饮片药品定义标识。 */
    private Long medicationId;
    /** 中药付数。 */
    private BigDecimal chineseHerbsDoseQuantity;
    /** 代煎标志。 */
    private Boolean sufferingFlag;
    /** 请求数量（单剂 × 付数）。 */
    private BigDecimal quantity;
    /** 数量单位编码。 */
    private String unitCode;
    /** 单次剂量。 */
    private BigDecimal dose;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 用法编码。 */
    private String methodCode;
    /** 频次编码。 */
    private String rateCode;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 中药扩展内容。 */
    private String contentJson;
    /** 用药说明。 */
    private String dosageInstruction;
    /** 备注。 */
    private String remark;
    /**
     * 绑定就诊诊断主键有序串（列 {@code encounter_diagnosis_ids}）；非空时更新。
     */
    private String encounterDiagnosisIds;
    /** 费用性质/合同覆盖。 */
    private Long contractId;
    /** 开立机构主键。 */
    private Long orgId;
    /** 发放药房库位主键。 */
    private Long performLocation;
    /** 医生站列表排序号。 */
    private Integer sortNumber;
    /** 处方类别编码。 */
    private String rxTypeCode;
    /** 处方号。 */
    private String prescriptionNo;
}
