/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 审核通过检查报告（REP-002）。
 */
@Getter
@Setter
public class ApprovePacsReportCommand {

    private Long reportId;

    private Long auditorId;

    private String auditComment;

    private Long operatorId;
}
