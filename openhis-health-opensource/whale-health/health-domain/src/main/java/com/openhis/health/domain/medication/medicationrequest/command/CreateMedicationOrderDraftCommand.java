/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    /**
     * 输液滴速编码（术语 {@code speed_code}；可空；非输液药可不传）。
     */
    private String speedCode;
    /** 单次剂量。 */
    private BigDecimal dose;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 是否皮试。 */
    private Boolean skinTestFlag;
    /**
     * 绑定就诊诊断主键有序串（医生站 {@code medicationPayload.encounterDiagnosisIds}，列 {@code encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    private String encounterDiagnosisIds;
    /** 费用性质/合同覆盖（列 {@code contract_id}；可空表示走患者默认）。 */
    private Long contractId;
    /** 药品成组号。 */
    private Long groupId;
    /** 首日量。 */
    private BigDecimal firstDose;
    /** 首日量单位编码。 */
    private String firstDoseUnitCode;
    /**
     * 分次剂量序列（计量、逗号分隔；仅长期西药/中成药；空=走 {@link #dose}；BR-MRQ-035）。
     */
    private String doseSchedule;
    /**
     * 分次用量序列（拆零、逗号分隔；与 {@link #doseSchedule} 同序；空=走 {@link #quantity}；BR-MRQ-035）。
     */
    private String quantitySchedule;

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

    /**
     * 医嘱备注（落库 {@code med_medication_request.remark}；护士站随医嘱展示）。
     */
    private String remark;

    /**
     * 来源表名（住院长嘱改量链：{@code med_medication_request}；可空表示非改量草稿）。
     *
     * <p>与 {@link #basedOnId} 成对；门诊半退等其它 based_on 语义由各自命令写入，勿混用。</p>
     */
    private String basedOnTable;

    /**
     * 来源主键（住院改量时=源长期药嘱 id；可空）。
     */
    private Long basedOnId;
}
