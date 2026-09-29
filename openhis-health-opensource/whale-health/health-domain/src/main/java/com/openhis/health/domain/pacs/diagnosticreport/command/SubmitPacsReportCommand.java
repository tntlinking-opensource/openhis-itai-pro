/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交检查报告待审。
 */
@Getter
@Setter
public class SubmitPacsReportCommand {

    private Long reportId;

    private Long operatorId;
}
