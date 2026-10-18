/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-OP-STK-02 皮试列表行。
 */
@Getter
@Setter
public class OutpatientSkinTestRowDto {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;

    /** 患者主键 {@code adm_patient.id}。 */
    private Long patientId;

    /** 皮试检查诊疗申请主键 {@code wkf_service_request.id}。 */
    private Long serviceRequestId;

    /** 关联药品申请主键 {@code med_medication_request.id}。 */
    private Long medicationRequestId;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 药品展示名称。 */
    private String medicationName;

    /** 开嘱医生主键。 */
    private Long openDoctorId;

    /** 开嘱医生姓名。 */
    private String openDoctorName;

    /** 执行/发药位置主键（药房或注射室）。 */
    private Long performLocationId;

    /** 已登记过敏/皮试记录主键；未登记时为 null。 */
    private Long allergyRecordId;

    /** 当前选用批号。 */
    private String lotNumber;

    /** 可选批号下拉（按药品+执行位置聚合库存）。 */
    private List<OutpatientSkinTestLotOptionDto> lotNumberOptions;

    /** 皮试登记时间。 */
    private LocalDateTime recordedDate;

    /** 临床状态 code。 */
    private String clinicalStatusCode;

    /** 临床状态展示文案。 */
    private String clinicalStatusText;

    /** 核实状态 code。 */
    private String verificationStatusCode;

    /** 核实状态展示文案。 */
    private String verificationStatusText;

    /** 危险程度 code。 */
    private String criticalityCode;

    /** 危险程度展示文案。 */
    private String criticalityText;

    /** 严重程度 code。 */
    private String severityCode;

    /** 严重程度展示文案。 */
    private String severityText;

    /** 执行护士主键。 */
    private Long practitionerId;

    /** 执行护士姓名。 */
    private String practitionerName;

    /** 核对护士主键。 */
    private Long checkPractitionerId;

    /** 核对护士姓名。 */
    private String checkPractitionerName;

    /** 皮试结果描述/备注。 */
    private String description;

    /** 列表已过滤 COMPLETED，恒 true。 */
    private Boolean feeSettledFlag;
}
