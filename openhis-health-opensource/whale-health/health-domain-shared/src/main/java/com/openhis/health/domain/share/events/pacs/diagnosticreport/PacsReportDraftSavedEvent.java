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
 * 检查报告草稿已保存。
 */
@Getter
@Setter
public class PacsReportDraftSavedEvent extends DomainEvent {

    private Long workItemId;

    private String statusEnum;

    public PacsReportDraftSavedEvent() {
        super();
    }

    public PacsReportDraftSavedEvent(Long reportId, Long workItemId, String statusEnum) {
        super(reportId, AggregateTypeConstants.PACS_DIAGNOSTIC_REPORT);
        this.workItemId = workItemId;
        this.statusEnum = statusEnum;
    }
}
