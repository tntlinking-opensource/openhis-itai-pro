/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.pacs.diagnosticreport;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查报告审核退回。
 */
@Getter
@Setter
public class PacsReportRejectedEvent extends DomainEvent {

    private Long workItemId;

    private String reason;

    private String statusEnum;

    public PacsReportRejectedEvent() {
        super();
    }

    public PacsReportRejectedEvent(Long reportId, Long workItemId, String reason, String statusEnum) {
        super(reportId, AggregateTypeConstants.PACS_DIAGNOSTIC_REPORT);
        this.workItemId = workItemId;
        this.reason = reason;
        this.statusEnum = statusEnum;
    }
}
