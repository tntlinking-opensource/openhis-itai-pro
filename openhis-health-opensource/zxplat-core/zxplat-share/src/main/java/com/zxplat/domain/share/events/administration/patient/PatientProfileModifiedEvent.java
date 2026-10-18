/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.patient;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 患者档案已修改事件。
 */
@Getter
@Setter
public class PatientProfileModifiedEvent extends DomainEvent {

    /**
     * 变更字段列表。
     */
    private List<String> changedFields;

    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 无参构造函数。
     */
    public PatientProfileModifiedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param changedFields 变更字段列表
     * @param operatorId 操作人标识
     */
    public PatientProfileModifiedEvent(Long patientId, List<String> changedFields, Long operatorId) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.changedFields = changedFields;
        this.operatorId = operatorId;
    }
}
