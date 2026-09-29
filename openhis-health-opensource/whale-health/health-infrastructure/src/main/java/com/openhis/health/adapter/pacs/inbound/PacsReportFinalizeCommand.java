/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.inbound;

import lombok.Getter;
import lombok.Setter;

/**
 * 现场报告 final / PDF 路径回传命令（序号 19 占位）。
 */
@Getter
@Setter
public class PacsReportFinalizeCommand {

    /** PacsWorkItem 主键。 */
    private Long workItemId;

    /** 外部报告号。 */
    private String externalReportNo;

    /** PDF 存储路径或 URL（占位）。 */
    private String pdfPath;

    /** 操作人。 */
    private Long operatorId;
}
