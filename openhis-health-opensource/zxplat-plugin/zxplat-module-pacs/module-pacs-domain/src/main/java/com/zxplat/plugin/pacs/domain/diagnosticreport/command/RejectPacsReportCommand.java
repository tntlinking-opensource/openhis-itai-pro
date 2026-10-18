/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 审核退回检查报告（REP-002）。
 */
@Getter
@Setter
public class RejectPacsReportCommand {

    private Long reportId;

    private String reason;

    private Long operatorId;
}
