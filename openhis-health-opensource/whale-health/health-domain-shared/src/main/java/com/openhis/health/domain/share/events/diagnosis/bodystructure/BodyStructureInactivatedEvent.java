/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.diagnosis.bodystructure;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 身体部位已停用事件。
 */
@Getter
@Setter
public class BodyStructureInactivatedEvent extends DomainEvent {
    /** 状态编码（BodyStructureInactivatedEvent）。 */
    private String statusEnum;

    /**
     * 处理Long bodyStructureId, String statusEnum逻辑。
     * @return 返回结果
     */
    public BodyStructureInactivatedEvent(Long bodyStructureId, String statusEnum) {
        super(bodyStructureId, AggregateTypeConstants.BODY_STRUCTURE);
        this.statusEnum = statusEnum;
    }
}
