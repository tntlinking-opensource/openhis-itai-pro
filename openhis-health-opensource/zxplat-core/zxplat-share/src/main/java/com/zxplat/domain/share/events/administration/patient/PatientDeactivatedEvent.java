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
 * 患者停用事件。
 */
@Getter
@Setter
public class PatientDeactivatedEvent extends DomainEvent {

    /**
     * 状态编码。
     */
    private String statusEnum;

    /**
     * 停用原因。
     */
    private String reason;

    /**
     * 无参构造函数。
     */
    public PatientDeactivatedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param statusEnum 状态编码
     * @param reason 停用原因
     */
    public PatientDeactivatedEvent(Long patientId, String statusEnum, String reason) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.statusEnum = statusEnum;
        this.reason = reason;
    }
}
