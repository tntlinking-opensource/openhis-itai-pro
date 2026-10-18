/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 保存皮试记录命令。
 */
@Getter
@Setter
public class SaveSkinTestRecordCommand {
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
    /** 类型枚举编码。 */
    private String typeEnum;
    /** 类别编码。 */
    private String categoryCode;
    /** criticality枚举编码。 */
    private String criticalityEnum;
    /** 编码。 */
    private String code;
    /** description。 */
    private String description;
    /** 严重程度枚举编码。 */
    private String severityEnum;
    /** 起病日期时间。 */
    private LocalDateTime onsetDateTime;
    /** 记录日期。 */
    private LocalDateTime recordedDate;
    /** last反应发生。 */
    private LocalDateTime lastReactionOccurrence;
    /** exposureroute。 */
    private String exposureRoute;
    /** 执业者主键。 */
    private Long practitionerId;
    /** 检查执业者主键。 */
    private Long checkPractitionerId;
    /** note。 */
    private String note;
}
