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
 * 患者地址全文更新事件。
 */
@Getter
@Setter
public class PatientAddressModifiedEvent extends DomainEvent {

    /**
     * 新地址全文。
     */
    private String address;

    /**
     * 无参构造函数。
     */
    public PatientAddressModifiedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param address 新地址全文
     */
    public PatientAddressModifiedEvent(Long patientId, String address) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.address = address;
    }
}
