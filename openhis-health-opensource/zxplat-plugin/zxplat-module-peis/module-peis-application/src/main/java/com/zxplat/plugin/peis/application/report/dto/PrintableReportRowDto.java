/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可打印报告列表行 DTO（IF-PEIS-REP-01）。
 */
@Data
public class PrintableReportRowDto {

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

    /** PDF 存储键。 */
    private String pdfStorageKey;

    /** 打印次数。 */
    private Integer printCount;
}
