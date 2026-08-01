/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建中药医嘱草稿命令（与西药 {@link CreateMedicationOrderDraftCommand} 分轨，BR-MRQ-011）。
 */
@Getter
@Setter
public class CreateHerbalMedicationOrderDraftCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 患者标识。 */
    private Long patientId;
    /** 饮片药品定义标识（{@code med_medication_definition.id}）。 */
    private Long medicationId;
    /** 中药标志；未传时由领域层默认为 true。 */
    private Boolean tcmFlag;
    /** 中药付数（列 {@code chinese_herbs_dose_quantity}）。 */
    private BigDecimal chineseHerbsDoseQuantity;
    /** 代煎标志。 */
    private Boolean sufferingFlag;
    /** 请求数量（单剂用量 × 付数，列 {@code quantity}）。 */
    private BigDecimal quantity;
    /** 数量单位编码。 */
    private String unitCode;
    /** 单次剂量（单剂用量）。 */
    private BigDecimal dose;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 用法编码。 */
    private String methodCode;
    /** 频次编码。 */
    private String rateCode;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 中药扩展内容（方头与交互 key 等 JSON）。 */
    private String contentJson;
    /** 用药说明。 */
    private String dosageInstruction;
    /** 备注。 */
    private String remark;
    /**
     * 绑定就诊诊断主键有序串（列 {@code encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    private String encounterDiagnosisIds;
    /** 费用性质/合同覆盖。 */
    private Long contractId;
    /** 开立机构主键。 */
    private Long orgId;
    /** 发放药房库位主键。 */
    private Long performLocation;
    /** 记录人/开嘱人主键。 */
    private Long recorderId;
    /** 医生站列表排序号。 */
    private Integer sortNumber;
    /** 生成来源持久化码。 */
    private String generateSourceEnum;
    /** 处方类别编码（签发前预写 {@code rx_type_code}）。 */
    private String rxTypeCode;
    /** 处方号（签发前预写；已存在则复用）。 */
    private String prescriptionNo;
}
