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
 * 检查报告 PDF 存储键已绑定。
 */
@Getter
@Setter
public class PacsReportPdfBoundEvent extends DomainEvent {

    private Long workItemId;

    private String pdfStorageKey;

    public PacsReportPdfBoundEvent() {
        super();
    }

    public PacsReportPdfBoundEvent(Long reportId, Long workItemId, String pdfStorageKey) {
        super(reportId, AggregateTypeConstants.PACS_DIAGNOSTIC_REPORT);
        this.workItemId = workItemId;
        this.pdfStorageKey = pdfStorageKey;
    }
}
