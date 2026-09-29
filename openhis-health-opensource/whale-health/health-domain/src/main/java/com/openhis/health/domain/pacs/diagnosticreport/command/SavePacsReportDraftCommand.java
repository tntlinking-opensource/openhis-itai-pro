/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存检查报告草稿。
 */
@Getter
@Setter
public class SavePacsReportDraftCommand {

    private Long reportId;

    private String findings;

    private String conclusion;

    private Long operatorId;
}
