/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.clinical.allergyhistory.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 患者过敏史列表行。
 */
@Getter
@Setter
public class PatientAllergyHistoryRowDto {

    /** 记录主键。 */
    private Long id;

    /** 患者主键。 */
    private Long patientId;

    /** 登记就诊主键。 */
    private Long encounterId;

    /** 来源类型 code。 */
    private String typeCode;

    /** 来源类型文案。 */
    private String typeText;

    /** 过敏原类别 code。 */
    private String categoryCode;

    /** 过敏原类别文案。 */
    private String categoryText;

    /** 过敏原展示名。 */
    private String allergenName;

    /** 临床状态 code。 */
    private String clinicalStatusCode;

    /** 临床状态文案。 */
    private String clinicalStatusText;

    /** 验证状态 code。 */
    private String verificationStatusCode;

    /** 验证状态文案。 */
    private String verificationStatusText;

    /** 严重程度 code。 */
    private String severityCode;

    /** 严重程度文案。 */
    private String severityText;

    /** 记录时间。 */
    private LocalDateTime recordedDate;

    /** 备注。 */
    private String note;

    /** 皮试阳性只读，禁止在过敏史页改删。 */
    private Boolean sourceReadOnly;
}
