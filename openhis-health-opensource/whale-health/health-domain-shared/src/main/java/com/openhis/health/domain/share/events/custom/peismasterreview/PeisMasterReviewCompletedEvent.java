/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.peismasterreview;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检总检完成事件（AS-PEIS-MAS-001 MAS-04；供 REP-001 可打印列表刷新）。
 */
@Getter
@Setter
public class PeisMasterReviewCompletedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /**
     * 构造总检完成事件。
     *
     * @param encounterId        就诊主键（事件锚点）
     * @param patientId          患者主键
     * @param diagnosticReportId 总检报告主键
     */
    public PeisMasterReviewCompletedEvent(Long encounterId, Long patientId, Long diagnosticReportId) {
        super(encounterId, AggregateTypeConstants.DIAGNOSTIC_REPORT);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.diagnosticReportId = diagnosticReportId;
    }
}
