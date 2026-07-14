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
 * 身体部位已创建事件。
 */
@Getter
@Setter
public class BodyStructureCreatedEvent extends DomainEvent {
    /** 业务No（BodyStructureCreatedEvent）。 */
    private String busNo;
    /** name（BodyStructureCreatedEvent）。 */
    private String name;
    /** 状态编码（BodyStructureCreatedEvent）。 */
    private String statusEnum;

    /**
     * 处理Long bodyStructureId, String busNo, String name, String statusEnum逻辑。
     * @return 返回结果
     */
    public BodyStructureCreatedEvent(Long bodyStructureId, String busNo, String name, String statusEnum) {
        super(bodyStructureId, AggregateTypeConstants.BODY_STRUCTURE);
        this.busNo = busNo;
        this.name = name;
        this.statusEnum = statusEnum;
    }
}
