/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.peisencounterext;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐快照调整事件（DM-PEIS-007 updatePackageSnapshot 后发布）。
 */
@Getter
@Setter
public class PeisEncounterPackageAdjustedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 构造套餐快照调整事件。
     *
     * @param encounterId 就诊主键
     */
    public PeisEncounterPackageAdjustedEvent(Long encounterId) {
        super(encounterId, AggregateTypeConstants.PEIS_ENCOUNTER_EXT);
        this.encounterId = encounterId;
    }
}
