/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.recheck.dto;

import lombok.Data;

/**
 * 复查列表行 DTO（IF-PEIS-REC-01）。
 */
@Data
public class RecheckRowDto {

    /** 任务主键。 */
    private Long taskId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 体检流水号。 */
    private String busNo;

    /** 任务状态 code。 */
    private String statusCode;

    /** 进度展示标签。 */
    private String progressLabel;

    /** 复查原因。 */
    private String reason;
}
