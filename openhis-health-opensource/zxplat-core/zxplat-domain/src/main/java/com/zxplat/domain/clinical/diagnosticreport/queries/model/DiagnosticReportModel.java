/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.diagnosticreport.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 临床总检报告读模型（DM-CLN-008 §4.6.1）。
 */
@Getter
@Setter
public class DiagnosticReportModel {

    /** 报告主键。 */
    private Long id;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 状态 code。 */
    private String statusEnum;

    /** 综述文本。 */
    private String summaryText;

    /** 结论文本。 */
    private String conclusionText;

    /** 结果引用 JSON。 */
    private String resultRefsJson;

    /** PDF 存储键。 */
    private String pdfStorageKey;

    /** 定稿时间。 */
    private LocalDateTime finalizedAt;

    /** 定稿人。 */
    private Long finalizedBy;
}
