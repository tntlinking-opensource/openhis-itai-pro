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
 * 检查报告已提交待审。
 */
@Getter
@Setter
public class PacsReportSubmittedEvent extends DomainEvent {

    private Long workItemId;

    private LocalDateTime submittedAt;

    private String statusEnum;

    public PacsReportSubmittedEvent() {
        super();
    }

    public PacsReportSubmittedEvent(
        Long reportId, Long workItemId, LocalDateTime submittedAt, String statusEnum) {
        super(reportId, AggregateTypeConstants.PACS_DIAGNOSTIC_REPORT);
        this.workItemId = workItemId;
        this.submittedAt = submittedAt;
        this.statusEnum = statusEnum;
    }
}
