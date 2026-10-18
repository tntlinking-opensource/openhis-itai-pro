/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.pacsintegration;

import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 分科诊台读取的检查报告摘要（SPI 视图，避免 PEIS 直依赖 PACS 模块）。
 */
@Getter
@Setter
public class PeisPacsReportSummaryView {

    /** 报告主键。 */
    private Long id;

    /** 报告状态编码（与 {@code PacsReportStatus} 的 code 对齐）。 */
    private String statusEnum;

    /** 诊断结论。 */
    private String conclusion;

    /** 影像所见。 */
    private String findings;
}
