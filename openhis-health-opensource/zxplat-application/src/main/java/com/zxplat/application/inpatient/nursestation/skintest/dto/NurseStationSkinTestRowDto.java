/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.skintest.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-STK-02 皮试列表行。
 */
@Getter
@Setter
public class NurseStationSkinTestRowDto {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 皮试药医嘱主键。 */
    private Long medicationRequestId;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 药品名称。 */
    private String medicationName;

    /** 开立医生主键。 */
    private Long openDoctorId;

    /** 开立医生展示名。 */
    private String openDoctorName;

    /** 发放意向库房主键。 */
    private Long performLocationId;

    /** 已有过敏记录主键（可空）。 */
    private Long allergyRecordId;

    /** doctor-prescription 皮试检查服务申请主键（一行一 companion SR）。 */
    private Long skinTestServiceRequestId;

    /** 已选批号（可空；保存时必填且须 ∈ 发放候选）。 */
    private String lotNumber;

    /** 批号候选列表（取自 MedicationDispense 去重 lot_number）。 */
    private List<NurseStationSkinTestLotOptionDto> lotNumberOptions;

    /** 记录时间（可空）。 */
    private LocalDateTime recordedDate;

    /** 临床状态 code。 */
    private String clinicalStatusCode;

    /** 临床状态展示文案。 */
    private String clinicalStatusText;

    /** 验证状态 code。 */
    private String verificationStatusCode;

    /** 验证状态展示文案。 */
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

    /** 执行护士展示名。 */
    private String practitionerName;

    /** 核对护士主键。 */
    private Long checkPractitionerId;

    /** 核对护士展示名。 */
    private String checkPractitionerName;

    /** 反应描述。 */
    private String description;

    /** 皮试检查是否已执行（净剩 COMPLETED Procedure；可登记结果）。 */
    private Boolean executedFlag;

    /**
     * 已登记只读标记（BR-IP-STK-003）：{@code allergyRecordId} 非空时为 true，前端禁改保存。
     */
    private Boolean readOnlyFlag;
}
