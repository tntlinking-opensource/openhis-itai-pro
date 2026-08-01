/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.diagnosticreport;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 临床总检报告已定稿事件（DM-CLN-008 finalizeMaster 后发布）。
 */
@Getter
@Setter
public class DiagnosticReportFinalizedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 定稿后状态 code。 */
    private String statusEnum;

    /**
     * 构造已定稿事件。
     *
     * @param id          报告主键
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param statusEnum  状态 code
     */
    public DiagnosticReportFinalizedEvent(Long id, Long encounterId, Long patientId, String statusEnum) {
        super(id, AggregateTypeConstants.DIAGNOSTIC_REPORT);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.statusEnum = statusEnum;
    }
}
