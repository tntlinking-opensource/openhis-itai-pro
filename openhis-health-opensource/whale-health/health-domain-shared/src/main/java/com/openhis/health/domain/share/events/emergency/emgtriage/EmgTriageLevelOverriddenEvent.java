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

package com.openhis.health.domain.share.events.emergency.emgtriage;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DM-EMG-001 §5.1：分级人工覆盖。
 */
@Getter
@Setter
public class EmgTriageLevelOverriddenEvent extends DomainEvent {

    private Long emgTriageId;
    private Integer oldLevel;
    private Integer newLevel;
    private String reason;
    private Long operatorId;

    public EmgTriageLevelOverriddenEvent() {
        super();
    }

    public EmgTriageLevelOverriddenEvent(
            Long emgTriageId, Integer oldLevel, Integer newLevel, String reason, Long operatorId) {
        super(emgTriageId, AggregateTypeConstants.EMERGENCY_TRIAGE);
        this.emgTriageId = emgTriageId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
