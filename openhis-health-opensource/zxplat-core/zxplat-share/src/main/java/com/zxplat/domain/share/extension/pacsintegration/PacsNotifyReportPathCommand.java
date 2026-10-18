/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.pacsintegration;

import lombok.Getter;
import lombok.Setter;

/**
 * 报告路径回传 HIS 命令（IF-PACS-INT-06）。
 */
@Getter
@Setter
public class PacsNotifyReportPathCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** 报告主键。 */
    private Long reportId;

    /** 检查号。 */
    private String accessionNumber;

    /** PDF 对象存储键。 */
    private String pdfStorageKey;

    /** 操作人（审核发布或手工补调）。 */
    private Long operatorId;
}
