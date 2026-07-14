/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.patient;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者手机号更新事件。
 */
@Getter
@Setter
public class PatientPhoneModifiedEvent extends DomainEvent {

    /**
     * 新手机号。
     */
    private String phone;

    /**
     * 无参构造函数。
     */
    public PatientPhoneModifiedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param phone 新手机号
     */
    public PatientPhoneModifiedEvent(Long patientId, String phone) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.phone = phone;
    }
}
