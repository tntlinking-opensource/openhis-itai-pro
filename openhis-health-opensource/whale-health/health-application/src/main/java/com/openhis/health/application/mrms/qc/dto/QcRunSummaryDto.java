/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qc.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质控执行批次摘要 DTO。
 */
@Getter
@Setter
public class QcRunSummaryDto {

    /** 质控批次主键。 */
    private Long qcRunId;

    /** 触发方式码（手动/自动等）。 */
    private String triggerCode;

    /** 是否通过（无阻断级开放缺陷）。 */
    private Boolean passed;

    /** 开放阻断级缺陷数。 */
    private Integer defectOpenCount;

    /** 开放警告级缺陷数。 */
    private Integer defectWarnCount;

    /** 质控完成时间。 */
    private LocalDateTime finishedAt;
}
