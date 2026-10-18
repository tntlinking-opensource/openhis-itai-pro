/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.patient;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 患者身份确认事件。
 */
@Getter
@Setter
public class PatientIdentityConfirmedEvent extends DomainEvent {

    /**
     * 院内编码。
     */
    private String busNo;

    /**
     * 状态编码。
     */
    private String statusEnum;

    /**
     * 确认时间。
     */
    private LocalDateTime confirmedAt;

    /**
     * 无参构造函数。
     */
    public PatientIdentityConfirmedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param busNo 院内编码
     * @param statusEnum 状态编码
     * @param confirmedAt 确认时间
     */
    public PatientIdentityConfirmedEvent(Long patientId, String busNo, String statusEnum, LocalDateTime confirmedAt) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.busNo = busNo;
        this.statusEnum = statusEnum;
        this.confirmedAt = confirmedAt;
    }
}
