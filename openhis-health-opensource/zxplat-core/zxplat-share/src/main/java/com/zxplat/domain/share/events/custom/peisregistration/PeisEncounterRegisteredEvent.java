/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.peisregistration;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 个检登记成功事件（AS-PEIS-REG-001 REG-02 后发布）。
 */
@Getter
@Setter
public class PeisEncounterRegisteredEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 体检流水号。 */
    private String busNo;

    /**
     * 构造个检登记成功事件。
     *
     * @param encounterId       就诊主键
     * @param patientId         患者主键
     * @param packageActivityId 套餐 AD
     * @param busNo             体检流水号
     */
    public PeisEncounterRegisteredEvent(
            Long encounterId, Long patientId, Long packageActivityId, String busNo) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.packageActivityId = packageActivityId;
        this.busNo = busNo;
    }
}
