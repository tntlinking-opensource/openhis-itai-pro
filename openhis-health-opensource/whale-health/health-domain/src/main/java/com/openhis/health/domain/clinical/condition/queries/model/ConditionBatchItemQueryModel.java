/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.condition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断记录批量项模型。
 */
@Getter
@Setter
public class ConditionBatchItemQueryModel {
    /** 主键。 */
    private Long id;
    /** 患者主键。 */
    private Long patientId;
    /** 目录主键。 */
    private Long definitionId;
    /** 核销状态枚举编码。 */
    private String verificationStatusEnum;
    /** 临床状态枚举编码。 */
    private String clinicalStatusEnum;
    /** 病情描述（快照展示兜底）。 */
    private String description;
    /** 医保编码。 */
    private String ybNo;
}
