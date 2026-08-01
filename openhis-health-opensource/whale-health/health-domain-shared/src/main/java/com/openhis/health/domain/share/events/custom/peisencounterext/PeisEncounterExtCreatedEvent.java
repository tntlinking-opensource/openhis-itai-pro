/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.peisencounterext;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检就诊扩展创建事件（DM-PEIS-007 createForRegistration 后发布）。
 */
@Getter
@Setter
public class PeisEncounterExtCreatedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /** 是否团检。 */
    private Boolean groupFlag;

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /**
     * 构造就诊扩展创建事件。
     *
     * @param encounterId       就诊主键
     * @param groupFlag         团检标志
     * @param packageActivityId 套餐 AD
     */
    public PeisEncounterExtCreatedEvent(Long encounterId, Boolean groupFlag, Long packageActivityId) {
        super(encounterId, AggregateTypeConstants.PEIS_ENCOUNTER_EXT);
        this.encounterId = encounterId;
        this.groupFlag = groupFlag;
        this.packageActivityId = packageActivityId;
    }
}
