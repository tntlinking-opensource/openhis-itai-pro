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
 * 里程碑已废弃事件。
 */
@Getter
@Setter
public class MilestoneRetiredEvent extends DomainEvent {
    /** 原因（MilestoneRetiredEvent）。 */
    private String reason;

    /** 操作人主键（MilestoneRetiredEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MilestoneRetiredEvent() {
        super();
    }

    /**
     * 处理Long encounterHistoryId, String reason逻辑。
     * @return 返回结果
     */
    public MilestoneRetiredEvent(Long encounterHistoryId, String reason, Long operatorId) {
        super(encounterHistoryId, AggregateTypeConstants.ENCOUNTER_HISTORY);
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
