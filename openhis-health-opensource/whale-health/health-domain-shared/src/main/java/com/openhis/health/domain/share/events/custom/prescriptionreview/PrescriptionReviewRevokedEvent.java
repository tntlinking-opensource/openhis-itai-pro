/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.prescriptionreview;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 处方点评已撤销事件（DM-CUS-015）。
 */
@Getter
@Setter
public class PrescriptionReviewRevokedEvent extends DomainEvent {

    /** 处方号。 */
    private String prescriptionNo;
    /** 处方业务号。 */
    private String busNo;
    /** 操作人。 */
    private Long operatorId;

    /**
     * 无参构造（框架反序列化）。
     */
    public PrescriptionReviewRevokedEvent() {
        super();
    }

    /**
     * 业务构造。
     *
     * @param id             点评主键
     * @param prescriptionNo 处方号
     * @param busNo          业务号
     * @param operatorId     操作人
     */
    public PrescriptionReviewRevokedEvent(Long id, String prescriptionNo, String busNo, Long operatorId) {
        super(id, AggregateTypeConstants.PRESCRIPTION_REVIEW);
        this.prescriptionNo = prescriptionNo;
        this.busNo = busNo;
        this.operatorId = operatorId;
    }
}
