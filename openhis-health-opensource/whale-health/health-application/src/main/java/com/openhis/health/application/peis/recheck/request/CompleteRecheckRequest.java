/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.recheck.request;

import lombok.Data;

/**
 * 复查完成请求（IF-PEIS-REC-03）。
 */
@Data
public class CompleteRecheckRequest {

    /** 复查任务主键。 */
    private Long taskId;

    /** 复查总检报告主键。 */
    private Long diagnosticReportId;

    /** 操作人标识。 */
    private Long operatorId;
}
