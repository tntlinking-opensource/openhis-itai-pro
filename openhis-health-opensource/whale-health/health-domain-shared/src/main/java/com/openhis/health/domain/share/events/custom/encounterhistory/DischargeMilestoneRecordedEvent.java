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
 * 离院里程碑已记录事件。
 */
@Getter
@Setter
public class DischargeMilestoneRecordedEvent extends DomainEvent {
    /** 就诊主键（DischargeMilestoneRecordedEvent）。 */
    private Long encounterId;
    /** outWay编码（DischargeMilestoneRecordedEvent）。 */
    private String outWayCode;

    /** 操作人主键（DischargeMilestoneRecordedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DischargeMilestoneRecordedEvent() {
        super();
    }

    /**
     * 处理Long encounterHistoryId, Long encounterId, String outWayCode逻辑。
     * @return 返回结果
     */
    public DischargeMilestoneRecordedEvent(Long encounterHistoryId, Long encounterId, String outWayCode, Long operatorId) {
        super(encounterHistoryId, AggregateTypeConstants.ENCOUNTER_HISTORY);
        this.encounterId = encounterId;
        this.outWayCode = outWayCode;
        this.operatorId = operatorId;
    }
}
