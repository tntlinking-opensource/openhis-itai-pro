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
 * 患者逻辑删除事件。
 */
@Getter
@Setter
public class PatientDeletedEvent extends DomainEvent {

    /**
     * 院内编码。
     */
    private String busNo;

    /**
     * 删除时间。
     */
    private LocalDateTime deletedAt;

    /**
     * 无参构造函数。
     */
    public PatientDeletedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param busNo 院内编码
     * @param deletedAt 删除时间
     */
    public PatientDeletedEvent(Long patientId, String busNo, LocalDateTime deletedAt) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.busNo = busNo;
        this.deletedAt = deletedAt;
    }
}
