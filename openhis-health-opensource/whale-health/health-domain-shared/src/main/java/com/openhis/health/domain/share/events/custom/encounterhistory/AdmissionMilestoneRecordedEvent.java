/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.custom.encounterhistory;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 入科里程碑已记录事件。
 */
@Getter
@Setter
public class AdmissionMilestoneRecordedEvent extends DomainEvent {
    private Long encounterId;

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
