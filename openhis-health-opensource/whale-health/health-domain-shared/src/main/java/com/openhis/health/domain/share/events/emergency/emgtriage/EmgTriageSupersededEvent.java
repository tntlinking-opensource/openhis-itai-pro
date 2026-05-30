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
 * DM-EMG-001 §5.1：急诊分诊记录已被后继二次分诊取代。
 */
@Getter
@Setter
public class EmgTriageSupersededEvent extends DomainEvent {

    private Long supersededEmgTriageId;
    private Long supersedingEmgTriageId;
    private Long encounterId;
    private Long patientId;

    public EmgTriageSupersededEvent() {
        super();
    }

    public EmgTriageSupersededEvent(
            Long supersededEmgTriageId,
            Long supersedingEmgTriageId,
            Long encounterId,
            Long patientId) {
        super(supersededEmgTriageId, AggregateTypeConstants.EMERGENCY_TRIAGE);
        this.supersededEmgTriageId = supersededEmgTriageId;
        this.supersedingEmgTriageId = supersedingEmgTriageId;
        this.encounterId = encounterId;
        this.patientId = patientId;
    }
}
