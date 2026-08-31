/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterprogress.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 可打印报告列表行读模型（DM-PEIS-008 REP-01）。
 */
@Getter
@Setter
public class PrintableReportRowModel {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 体检流水号。 */
    private String busNo;

    /** 定稿时间。 */
    private LocalDateTime finalizedAt;

    /** PDF 存储键（可为空，待 PrintTemplate 回写）。 */
    private String pdfStorageKey;

    /** 打印次数。 */
    private Integer printCount;
}
