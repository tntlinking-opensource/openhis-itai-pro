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
 * 频次已删除事件。
 */
@Getter
@Setter
public class FrequencyDeletedEvent extends DomainEvent {
    /**
     * 频次编码。
     */
    private String rateCode;

    /** 操作人主键（FrequencyDeletedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public FrequencyDeletedEvent() {
        super();
    }

    /**
     * 处理Long id, String rateCode逻辑。
     * @return 返回结果
     */
    public FrequencyDeletedEvent(Long id, String rateCode, Long operatorId) {
        super(id, AggregateTypeConstants.FREQUENCY);
        this.rateCode = rateCode;
        this.operatorId = operatorId;
    }
}
