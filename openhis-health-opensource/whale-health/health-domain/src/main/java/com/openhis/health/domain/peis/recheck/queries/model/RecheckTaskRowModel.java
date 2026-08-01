/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.recheck.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 复查任务列表行读模型。
 */
@Getter
@Setter
public class RecheckTaskRowModel {

    /** 任务主键。 */
    private Long taskId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 任务状态 code。 */
    private String statusCode;

    /** 复查原因。 */
    private String reason;

    /** 关联总检报告主键。 */
    private Long diagnosticReportId;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
