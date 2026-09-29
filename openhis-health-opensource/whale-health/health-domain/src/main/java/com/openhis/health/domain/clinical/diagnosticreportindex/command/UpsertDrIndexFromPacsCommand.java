/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreportindex.command;

import lombok.Getter;
import lombok.Setter;

/**
 * PACS 报告审核通过后 upsert 读侧索引命令（DM-CLN-006）。
 */
@Getter
@Setter
public class UpsertDrIndexFromPacsCommand {

    /** PACS 报告主键。 */
    private Long sourceReportId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 主服务申请（可选）。 */
    private Long serviceRequestId;

    /** 列表标题。 */
    private String title;

    /** PDF 存储键。 */
    private String pdfStorageKey;

    /** 租户主键。 */
    private Long tenantId;

    /** 机构主键。 */
    private Long orgId;
}
