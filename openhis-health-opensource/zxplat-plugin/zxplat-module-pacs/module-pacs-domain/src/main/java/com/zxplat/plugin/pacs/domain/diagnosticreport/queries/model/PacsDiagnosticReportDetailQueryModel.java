/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.diagnosticreport.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查报告详情读模型。
 */
@Getter
@Setter
public class PacsDiagnosticReportDetailQueryModel {

    private Long id;

    private Long workItemId;

    private Long patientId;

    private Long encounterId;

    private Long organizationId;

    private Long serviceRequestId;

    private String statusEnum;

    private String findings;

    private String conclusion;

    private String pdfStorageKey;

    private Long authorPractitionerId;

    private LocalDateTime submittedAt;

    private Long submittedBy;

    private LocalDateTime issuedAt;

    private Long auditorPractitionerId;

    private Boolean pendingAudit;
}
