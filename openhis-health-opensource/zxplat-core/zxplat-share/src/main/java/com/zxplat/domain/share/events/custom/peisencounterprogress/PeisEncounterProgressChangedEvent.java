/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.peisencounterprogress;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检进度变更信号事件（DM-PEIS-008 signalProgressChanged 后发布，供 MAS/缓存订阅）。
 */
@Getter
@Setter
public class PeisEncounterProgressChangedEvent extends DomainEvent {

    /** 进度快照 code（可选）。 */
    private String progressCode;

    /** 触发来源（DES/MAS/INT）。 */
    private String triggerSource;

    /**
     * 构造进度变更事件。
     *
     * @param encounterId   就诊主键
     * @param progressCode  进度 code
     * @param triggerSource 触发来源
     */
    public PeisEncounterProgressChangedEvent(Long encounterId, String progressCode, String triggerSource) {
        super(encounterId, AggregateTypeConstants.PEIS_ENCOUNTER_EXT);
        this.progressCode = progressCode;
        this.triggerSource = triggerSource;
    }
}
