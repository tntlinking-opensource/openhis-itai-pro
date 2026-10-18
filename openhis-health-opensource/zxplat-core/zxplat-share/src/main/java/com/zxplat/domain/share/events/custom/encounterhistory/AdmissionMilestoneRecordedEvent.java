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
 * 入科里程碑已记录事件。
 */
@Getter
@Setter
public class AdmissionMilestoneRecordedEvent extends DomainEvent {
    /** 就诊主键（AdmissionMilestoneRecordedEvent）。 */
    private Long encounterId;

    /** 操作人主键（AdmissionMilestoneRecordedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public AdmissionMilestoneRecordedEvent() {
        super();
    }

    /**
     * 处理Long encounterHistoryId, Long encounterId逻辑。
     * @return 返回结果
     */
    public AdmissionMilestoneRecordedEvent(Long encounterHistoryId, Long encounterId, Long operatorId) {
        super(encounterHistoryId, AggregateTypeConstants.ENCOUNTER_HISTORY);
        this.encounterId = encounterId;
        this.operatorId = operatorId;
    }
}
