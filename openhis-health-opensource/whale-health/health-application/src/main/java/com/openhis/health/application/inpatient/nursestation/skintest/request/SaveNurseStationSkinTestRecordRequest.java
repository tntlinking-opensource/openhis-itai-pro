/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.skintest.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * IF-IP-STK-03 保存皮试结果请求。
 */
@Getter
@Setter
public class SaveNurseStationSkinTestRecordRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 账户主键（首次补费必填）。 */
    private Long accountId;

    /** 皮试药医嘱主键。 */
    private Long medicationRequestId;

    /**
     * 目标 companion 皮试检查 SR 主键；同一皮试药多条 companion 时必填，单条时可空由服务端解析。
     */
    private Long skinTestServiceRequestId;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 已有过敏记录主键（更新时必填）。 */
    private Long allergyRecordId;

    /** 批号（必填，且须 ∈ 该皮试药 MedicationDispense 发放批号候选，BR-IP-STK-013）。 */
    private String lotNumber;

    /** 记录时间；空则服务端取当前时刻。 */
    private LocalDateTime recordedDate;

    /** 临床状态 code。 */
    private String clinicalStatusCode;

    /** 验证状态 code。 */
    private String verificationStatusCode;

    /** 危险程度 code。 */
    private String criticalityCode;

    /** 严重程度 code。 */
    private String severityCode;

    /** 反应描述。 */
    private String description;

    /** 执行护士主键。 */
    private Long practitionerId;

    /** 核对护士主键。 */
    private Long checkPractitionerId;
}
