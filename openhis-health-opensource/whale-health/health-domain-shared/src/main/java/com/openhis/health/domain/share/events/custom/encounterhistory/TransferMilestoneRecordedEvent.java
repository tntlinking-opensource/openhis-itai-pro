/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.encounterhistory;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 转科里程碑已记录事件。
 */
@Getter
@Setter
public class TransferMilestoneRecordedEvent extends DomainEvent {
    /** 就诊主键（TransferMilestoneRecordedEvent）。 */
    private Long encounterId;
    /** request主键（TransferMilestoneRecordedEvent）。 */
    private Long requestId;

    /** 操作人主键（TransferMilestoneRecordedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TransferMilestoneRecordedEvent() {
        super();
    }

    /**
     * 处理Long encounterHistoryId, Long encounterId, Long requestId逻辑。
     * @return 返回结果
     */
    public TransferMilestoneRecordedEvent(Long encounterHistoryId, Long encounterId, Long requestId, Long operatorId) {
        super(encounterHistoryId, AggregateTypeConstants.ENCOUNTER_HISTORY);
        this.encounterId = encounterId;
        this.requestId = requestId;
        this.operatorId = operatorId;
    }
}
