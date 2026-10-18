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
 * 患者非身份证标识同步事件。
 */
@Getter
@Setter
public class PatientIdentifiersSyncedEvent extends DomainEvent {

    /**
     * 同步后的标识数量。
     */
    private Integer identifierCount;

    /**
     * 无参构造函数。
     */
    public PatientIdentifiersSyncedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param identifierCount 同步后的标识数量
     */
    public PatientIdentifiersSyncedEvent(Long patientId, Integer identifierCount) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.identifierCount = identifierCount;
    }
}
