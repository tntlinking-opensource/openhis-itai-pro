/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreportindex.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消审核时吊销 PACS 读侧索引（标 cancelled，不软删以免唯一键冲突）。
 */
@Getter
@Setter
public class RevokeDrIndexFromPacsCommand {

    /** PACS 报告主键（= sourceReportId）。 */
    private Long sourceReportId;

    /** 操作人。 */
    private Long operatorId;
}
