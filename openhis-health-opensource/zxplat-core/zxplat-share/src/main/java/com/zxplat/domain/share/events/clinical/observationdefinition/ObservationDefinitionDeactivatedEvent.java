/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.observationdefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 临床观察定义已停用事件（DM-CLN-005）。
 */
@Getter
@Setter
public class ObservationDefinitionDeactivatedEvent extends DomainEvent {

    /**
     * 构造停用事件。
     *
     * @param id 观察定义主键
     */
    public ObservationDefinitionDeactivatedEvent(Long id) {
        super(id, AggregateTypeConstants.OBSERVATION_DEFINITION);
    }
}
