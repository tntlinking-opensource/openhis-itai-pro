/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qcworkbench.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质控缺陷 DTO。
 */
@Getter
@Setter
public class QcDefectDto {

    /** 缺陷主键。 */
    private Long defectId;

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 质控规则编码。 */
    private String ruleCode;

    /** 缺陷描述信息。 */
    private String defectMessage;

    /** 严重级别码（阻断/警告等）。 */
    private String severityCode;

    /** 缺陷状态码（开放/已整改/已关闭等）。 */
    private String statusCode;

    /** 责任医师主键。 */
    private Long responsiblePractitionerId;

    /** 整改完成时间。 */
    private LocalDateTime rectifiedAt;

    /** 关闭时间。 */
    private LocalDateTime closedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
