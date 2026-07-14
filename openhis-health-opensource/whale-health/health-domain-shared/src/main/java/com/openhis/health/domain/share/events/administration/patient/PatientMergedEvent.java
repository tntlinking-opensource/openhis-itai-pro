/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.patient;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import java.time.LocalDateTime;

import com.openhis.whale.core.domain.event.DomainEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * 患者合并事件。
 */
@Getter
@Setter
public class PatientMergedEvent extends DomainEvent {

    /**
     * 源患者标识。
     */
    private Long sourcePatientId;

    /**
     * 目标患者标识。
     */
    private Long targetPatientId;

    /**
     * 源患者状态编码。
     */
    private String statusEnum;

    /**
     * 合并时间。
     */
    private LocalDateTime mergedAt;

    /**
     * 合并原因。
     */
    private String reason;

    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 无参构造函数。
     */
    public PatientMergedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param sourcePatientId 源患者标识
     * @param targetPatientId 目标患者标识
     * @param statusEnum 源患者状态编码
     * @param mergedAt 合并时间
     * @param reason 合并原因
     * @param operatorId 操作人标识
     */
    public PatientMergedEvent(Long sourcePatientId, Long targetPatientId, String statusEnum, LocalDateTime mergedAt,
        String reason, Long operatorId) {
        super(sourcePatientId, AggregateTypeConstants.PATIENT);
        this.sourcePatientId = sourcePatientId;
        this.targetPatientId = targetPatientId;
        this.statusEnum = statusEnum;
        this.mergedAt = mergedAt;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
