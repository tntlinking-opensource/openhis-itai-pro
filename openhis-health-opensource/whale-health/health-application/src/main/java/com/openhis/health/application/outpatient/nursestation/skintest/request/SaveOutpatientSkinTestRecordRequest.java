/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * IF-OP-STK-03 保存皮试临床结果请求。
 */
@Getter
@Setter
public class SaveOutpatientSkinTestRecordRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 皮试检查服务申请主键（列表锚点）。 */
    private Long serviceRequestId;

    /** 皮试药药品申请主键。 */
    private Long medicationRequestId;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 已有过敏记录主键（更新时）。 */
    private Long allergyRecordId;

    /** 批号。 */
    private String lotNumber;

    /** 记录时间。 */
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
