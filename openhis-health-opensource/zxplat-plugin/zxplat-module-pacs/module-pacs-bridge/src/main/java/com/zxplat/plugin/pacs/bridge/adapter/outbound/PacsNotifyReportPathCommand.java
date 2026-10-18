/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.adapter.outbound;

import lombok.Getter;
import lombok.Setter;

/**
 * 报告路径回传 HIS 命令（IF-PACS-INT-06）。
 */
@Getter
@Setter
public class PacsNotifyReportPathCommand {

    private Long workItemId;

    private Long reportId;

    private String accessionNumber;

    private String pdfStorageKey;

    private Long operatorId;
}
