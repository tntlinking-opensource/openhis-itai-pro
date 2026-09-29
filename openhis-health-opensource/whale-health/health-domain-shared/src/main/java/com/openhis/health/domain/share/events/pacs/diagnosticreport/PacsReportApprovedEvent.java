/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.pacs.diagnosticreport;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查报告审核通过。
 */
@Getter
@Setter
public class PacsReportApprovedEvent extends DomainEvent {

    private Long workItemId;

    private LocalDateTime issuedAt;

    private String statusEnum;

    public PacsReportApprovedEvent() {
        super();
    }

    public PacsReportApprovedEvent(
        Long reportId, Long workItemId, LocalDateTime issuedAt, String statusEnum) {
        super(reportId, AggregateTypeConstants.PACS_DIAGNOSTIC_REPORT);
        this.workItemId = workItemId;
        this.issuedAt = issuedAt;
        this.statusEnum = statusEnum;
    }
}
