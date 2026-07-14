/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.procedure;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 执行记录已取消事件。
 */
@Getter
@Setter
/**
 * ProcedureRecordCancelledEvent。
 */
public class ProcedureRecordCancelledEvent extends DomainEvent {
    /**
     * statusEnum。
     */
    private String statusEnum;
    /**
     * statusReasonText。
     */
    private String statusReasonText;
    /**
     * refundId。
     */
    private Long refundId;

    public ProcedureRecordCancelledEvent(Long procedureId, String statusEnum, String statusReasonText, Long refundId) {
        super(procedureId, AggregateTypeConstants.PROCEDURE);
        this.statusEnum = statusEnum;
        this.statusReasonText = statusReasonText;
        this.refundId = refundId;
    }
}
