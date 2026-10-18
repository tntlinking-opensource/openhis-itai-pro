/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.pacs.diagnosticreport;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查报告取消审核（final → 待审）。
 */
@Getter
@Setter
public class PacsReportAuditCancelledEvent extends DomainEvent {

    /** 工单主键。 */
    private Long workItemId;

    /** 取消原因。 */
    private String reason;

    /** 回退后状态（应为 preliminary）。 */
    private String statusEnum;

    public PacsReportAuditCancelledEvent() {
        super();
    }

    /**
     * @param reportId   报告主键
     * @param workItemId 工单主键
     * @param reason     取消原因
     * @param statusEnum 回退后状态
     */
    public PacsReportAuditCancelledEvent(Long reportId, Long workItemId, String reason, String statusEnum) {
        super(reportId, AggregateTypeConstants.PACS_DIAGNOSTIC_REPORT);
        this.workItemId = workItemId;
        this.reason = reason;
        this.statusEnum = statusEnum;
    }
}
