/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 保存主诉过敏史请求。
 */
@Getter
@Setter
public class SavePatientAllergyHistoryRequest {

    /** 记录主键；空则新增。 */
    private Long id;

    /** 患者主键。 */
    private Long patientId;

    /** 当前就诊主键。 */
    private Long encounterId;

    /** 过敏原类别 code。 */
    private String categoryCode;

    /** 过敏原名称。 */
    private String description;

    /** 临床状态 code；空则按有效过敏写入。 */
    private String clinicalStatusCode;

    /** 验证状态 code。 */
    private String verificationStatusCode;

    /** 严重程度 code。 */
    private String severityCode;

    /** 危险程度 code。 */
    private String criticalityCode;

    /** 发生时间。 */
    private LocalDateTime onsetDateTime;

    /** 备注。 */
    private String note;
}
