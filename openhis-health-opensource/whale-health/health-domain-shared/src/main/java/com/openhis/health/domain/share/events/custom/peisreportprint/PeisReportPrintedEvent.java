/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.peisreportprint;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检报告确认打印事件（AS-PEIS-REP-001 REP-03；供发放/统计订阅）。
 */
@Getter
@Setter
public class PeisReportPrintedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 构造确认打印事件。
     *
     * @param encounterId        就诊主键（事件锚点）
     * @param diagnosticReportId 总检报告主键
     * @param operatorId         操作人标识
     */
    public PeisReportPrintedEvent(Long encounterId, Long diagnosticReportId, Long operatorId) {
        super(encounterId, AggregateTypeConstants.DIAGNOSTIC_REPORT);
        this.encounterId = encounterId;
        this.diagnosticReportId = diagnosticReportId;
        this.operatorId = operatorId;
    }
}
