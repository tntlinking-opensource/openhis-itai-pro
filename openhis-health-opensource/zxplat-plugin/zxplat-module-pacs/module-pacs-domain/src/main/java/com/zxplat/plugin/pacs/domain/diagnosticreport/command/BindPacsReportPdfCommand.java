/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 绑定检查报告 PDF 存储键。
 */
@Getter
@Setter
public class BindPacsReportPdfCommand {

    private Long reportId;

    private String pdfStorageKey;

    private Long operatorId;
}
