/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.patient;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 临时建档废弃事件。
 */
@Getter
@Setter
public class PatientDraftDiscardedEvent extends DomainEvent {

    /**
     * 院内编码。
     */
    private String busNo;

    /**
     * 无参构造函数。
     */
    public PatientDraftDiscardedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param busNo 院内编码
     */
    public PatientDraftDiscardedEvent(Long patientId, String busNo) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.busNo = busNo;
    }
}
