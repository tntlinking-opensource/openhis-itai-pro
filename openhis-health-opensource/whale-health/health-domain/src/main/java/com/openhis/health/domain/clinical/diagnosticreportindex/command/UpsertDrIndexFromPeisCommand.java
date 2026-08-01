/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreportindex.command;

import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 总检完成后 upsert 读侧索引命令（DM-CLN-006 §5.1.3）。
 */
@Getter
@Setter
public class UpsertDrIndexFromPeisCommand {

    /** PEIS 总检报告主键（cln_diagnostic_report.id）。 */
    private Long sourceReportId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 列表标题。 */
    private String title;

    /** 租户主键。 */
    private Long tenantId;

    /** 机构主键。 */
    private Long orgId;
}
