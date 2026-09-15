/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.allergyintolerance.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 保存主诉过敏史命令（医生或住院护士登记，不绑皮试申请）。
 */
@Getter
@Setter
public class SaveDeclaredAllergyRecordCommand {
    /** 主键；空则新增。 */
    private Long id;
    /** 就诊主键（登记时所在就诊，仅审计）。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 过敏原类别编码。 */
    private String categoryCode;
    /** 过敏原名称或描述。 */
    private String description;
    /** 临床状态编码；空则按有效过敏写入 active。 */
    private String clinicalStatusEnum;
    /** 验证状态编码。 */
    private String verificationStatusEnum;
    /** 危险程度编码。 */
    private String criticalityEnum;
    /** 严重程度编码。 */
    private String severityEnum;
    /** 发生时间。 */
    private LocalDateTime onsetDateTime;
    /** 记录时间；空则当前时刻。 */
    private LocalDateTime recordedDate;
    /** 记录者医务人员主键。 */
    private Long practitionerId;
    /** 备注。 */
    private String note;
}
