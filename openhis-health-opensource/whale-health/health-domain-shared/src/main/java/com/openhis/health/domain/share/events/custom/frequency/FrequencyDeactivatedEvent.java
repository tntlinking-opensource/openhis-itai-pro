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
 * 频次已停用事件。
 */
@Getter
@Setter
public class FrequencyDeactivatedEvent extends DomainEvent {
    /**
     * 频次编码。
     */
    private String rateCode;
    /**
     * 停用原因（可空）。
     */
    private String reason;

    /** 操作人主键（FrequencyDeactivatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public FrequencyDeactivatedEvent() {
        super();
    }

    /**
     * 处理Long id, String rateCode, String reason逻辑。
     * @return 返回结果
     */
    public FrequencyDeactivatedEvent(Long id, String rateCode, String reason, Long operatorId) {
        super(id, AggregateTypeConstants.FREQUENCY);
        this.rateCode = rateCode;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
