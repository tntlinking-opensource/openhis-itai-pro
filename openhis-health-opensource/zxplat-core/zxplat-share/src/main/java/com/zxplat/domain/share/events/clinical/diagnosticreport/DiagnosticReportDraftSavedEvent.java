/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.diagnosticreport;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 临床总检报告草稿已保存事件（DM-CLN-008 saveMasterDraft 后发布）。
 */
@Getter
@Setter
public class DiagnosticReportDraftSavedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 构造草稿已保存事件。
     *
     * @param id          报告主键
     * @param encounterId 就诊主键
     */
    public DiagnosticReportDraftSavedEvent(Long id, Long encounterId) {
        super(id, AggregateTypeConstants.DIAGNOSTIC_REPORT);
        this.encounterId = encounterId;
    }
}
