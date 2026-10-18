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
 * 患者重新启用事件。
 */
@Getter
@Setter
public class PatientReactivatedEvent extends DomainEvent {

    /**
     * 状态编码。
     */
    private String statusEnum;

    /**
     * 无参构造函数。
     */
    public PatientReactivatedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param statusEnum 状态编码
     */
    public PatientReactivatedEvent(Long patientId, String statusEnum) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.statusEnum = statusEnum;
    }
}
