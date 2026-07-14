/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.frequency;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 频次已创建事件。
 */
@Getter
@Setter
public class FrequencyCreatedEvent extends DomainEvent {
    /**
     * 频次编码。
     */
    private String rateCode;
    /**
     * 频次名称。
     */
    private String name;
    /**
     * 周期类型 code。
     */
    private String cycleType;

    /** 操作人主键（FrequencyCreatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public FrequencyCreatedEvent() {
        super();
    }

    /**
     * 处理Long id, String rateCode, String name, Integer cycleType逻辑。
     * @return 返回结果
     */
    public FrequencyCreatedEvent(Long id, String rateCode, String name, String cycleType, Long operatorId) {
        super(id, AggregateTypeConstants.FREQUENCY);
        this.rateCode = rateCode;
        this.name = name;
        this.cycleType = cycleType;
        this.operatorId = operatorId;
    }
}
