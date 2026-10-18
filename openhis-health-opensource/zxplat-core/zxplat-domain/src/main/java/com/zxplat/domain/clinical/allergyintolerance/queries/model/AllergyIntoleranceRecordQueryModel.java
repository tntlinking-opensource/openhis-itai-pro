/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 过敏记录查询模型。
 */
@Getter
@Setter
public class AllergyIntoleranceRecordQueryModel {
    /** 主键。 */
    private Long id;
    /** 请求主键。 */
    private Long requestId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 药品主键。 */
    private Long medicationId;
    /** 批号号。 */
    private String lotNumber;
    /** 临床状态枚举编码。 */
    private String clinicalStatusEnum;
    /** 核销状态枚举编码。 */
    private String verificationStatusEnum;
    /** criticality枚举编码。 */
    private String criticalityEnum;
    /** 严重程度枚举编码。 */
    private String severityEnum;
    /** 来源类型编码。 */
    private String typeEnum;
    /** 过敏原类别编码。 */
    private String categoryCode;
    /** 过敏物质编码。 */
    private String code;
    /** 记录日期。 */
    private LocalDateTime recordedDate;
    /** 反应描述。 */
    private String description;
    /** 备注。 */
    private String note;
    /** 发生时间。 */
    private LocalDateTime onsetDateTime;
    /** 记录者（执行护士）主键。 */
    private Long practitionerId;
    /** 断言者（核对护士）主键。 */
    private Long checkPractitionerId;
}
