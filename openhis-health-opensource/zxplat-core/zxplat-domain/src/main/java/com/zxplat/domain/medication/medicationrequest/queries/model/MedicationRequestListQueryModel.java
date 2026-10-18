/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医嘱列表查询模型。
 */
@Getter
@Setter
public class MedicationRequestListQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 处方号。 */
    private String prescriptionNo;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 原药品申请主键（退药逆向行非空；处置单仅展示正向行）。 */
    private Long refundMedicineId;
    /** 分组主键。 */
    private Long groupId;
    /**
     * 组套标识（{@code med_medication_request.package_id}；成组输液绑定耗材派生时可回写同源值）。
     */
    private Long packageId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 药品定义主键。 */
    private Long medicationId;
    /** 生成来源持久化码。 */
    private String generateSourceEnum;
    /** 记录人主键（审签/补录等，与开嘱人可能不同）。 */
    private Long recorderId;
    /**
     * 开嘱人（申请方医师）主键，与写模型 {@code med_medication_request.requester_id} 对齐；未落库时由装配层回落 {@link #recorderId}。
     */
    private Long requesterId;
    /** 开立时间（{@code req_opened_time}）。 */
    private LocalDateTime reqOpenedTime;
    /** 签发时间（{@code req_authored_time}）。 */
    private LocalDateTime reqAuthoredTime;
    /** 医生站列表展示排序号。 */
    private Integer sortNumber;
    /** 数量。 */
    private BigDecimal quantity;
    /** 单位编码。 */
    private String unitCode;
    /** 给药途径/用法编码。 */
    private String methodCode;
    /** 用药频次编码。 */
    private String rateCode;
    /** 单次剂量。 */
    private BigDecimal dose;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 首日/首次用量（仅长期；可空；BR-IP-ORD-030 / BR-MRQ-010）。 */
    private BigDecimal firstDose;
    /** 首日/首次用量单位编码（与 {@link #firstDose} 成对）。 */
    private String firstDoseUnitCode;
    /**
     * 分次剂量序列（计量、逗号分隔；可空；有值时列表展示走序列；BR-MRQ-035 / BR-IP-ORD-037）。
     */
    private String doseSchedule;
    /**
     * 分次用量序列（拆零、逗号分隔；与 {@link #doseSchedule} 同序；空=执行侧走 {@link #quantity}）。
     */
    private String quantitySchedule;
    /** 产品批号。 */
    private String lotNumber;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 开立科室组织主键（持久化 {@code org_id}，{@code adm_organization.id}）。 */
    private Long orgId;
    /** 发放药房库位主键（持久化 {@code perform_location}，{@code adm_location.id}）。 */
    private Long performLocation;
    /** 绑定就诊诊断主键有序串（持久化 {@code med_medication_request.encounter_diagnosis_ids}；有序，首位为主诊断）。 */
    private String encounterDiagnosisIds;
    /** 备注。 */
    private String remark;
    /** 请求扩展 JSON（编辑卡片回填）。 */
    private String contentJson;
    /** 处方类别编码。 */
    private String rxTypeCode;
    /** 费用性质主键（持久化 {@code med_medication_request.contract_id}）。 */
    private Long contractId;
    /** 用药说明编码（与定义/域列对齐）。 */
    private String dosageInstruction;
    /** 滴速编码。 */
    private String speedCode;
    /** 中药付数（持久化可能为小数，列表行再取整）。 */
    private BigDecimal chineseHerbsDoseQuantity;
    /** 代煎标识。 */
    private Boolean sufferingFlag;
    /** 是否皮试。 */
    private Boolean skinTestFlag;
    /** 是否输液/注射相关（域列 infusion_flag）。 */
    private Boolean infusionFlag;
    /** 前置业务锚表名（皮试关联等）。 */
    private String basedOnTable;
    /** 前置业务主键。 */
    private Long basedOnId;
    /** 校对人（持久化 performer_check_id，BR-MRQ-007）。 */
    private Long performerCheckId;
    /** 校对时间（持久化 check_time，BR-MRQ-007）。 */
    private LocalDateTime checkTime;
    /** 用药开始时间（持久化 effective_dose_start）。 */
    private LocalDateTime effectiveDoseStart;
    /** 服药计划结束时间（临床计划窗；非停嘱）。 */
    private LocalDateTime effectiveDoseEnd;
    /** 停嘱时间（{@code stop_time}；STOPPED 时有值）。 */
    private LocalDateTime stopTime;
    /** 停嘱人主键（{@code stopper_id}；未停 null）。 */
    private Long stopperId;
    /** 停嘱校对人主键（{@code stop_performer_check_id}；未停嘱校对 null）。 */
    private Long stopPerformerCheckId;
    /** 停嘱校对时间（{@code stop_check_time}）。 */
    private LocalDateTime stopCheckTime;
    /** 是否中医相关（付数等场景）。 */
    private Boolean tcmFlag;
    /** 治疗类型枚举编码（持久化 therapy_enum，与 {@code TherapyType#getCode()} 对齐）。 */
    private String therapyEnum;
}
