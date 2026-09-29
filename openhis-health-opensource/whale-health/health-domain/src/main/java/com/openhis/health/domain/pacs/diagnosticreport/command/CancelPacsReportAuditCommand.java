/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消审核：已发布报告回退为待审（IF-PACS-REP-09）。
 */
@Getter
@Setter
public class CancelPacsReportAuditCommand {

    /** 报告主键。 */
    private Long reportId;

    /** 取消原因（必填，落事件）。 */
    private String reason;

    /** 操作人。 */
    private Long operatorId;
}
