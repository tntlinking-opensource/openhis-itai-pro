/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.encounterhistory;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 里程碑已更正事件。
 */
@Getter
@Setter
public class MilestoneCorrectedEvent extends DomainEvent {
    /** 原因（MilestoneCorrectedEvent）。 */
    private String reason;

    /** 操作人主键（MilestoneCorrectedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MilestoneCorrectedEvent() {
        super();
    }

    /**
     * 处理Long encounterHistoryId, String reason逻辑。
     * @return 返回结果
     */
    public MilestoneCorrectedEvent(Long encounterHistoryId, String reason, Long operatorId) {
        super(encounterHistoryId, AggregateTypeConstants.ENCOUNTER_HISTORY);
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
