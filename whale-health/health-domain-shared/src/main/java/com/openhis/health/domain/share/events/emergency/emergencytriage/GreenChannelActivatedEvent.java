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

package com.openhis.health.domain.share.events.emergency.emergencytriage;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * GreenChannelActivatedEvent。
 */
public class GreenChannelActivatedEvent extends DomainEvent {
    /**
     * patientId。
     */
    private Long patientId;
    /**
     * encounterId。
     */
    private Long encounterId;
    /**
     * triageLevelEnum。
     */
    private String triageLevelEnum;

    /**
     * 处理Long triageId, Long patientId, Long encounterId, Integer triageLevelEnum逻辑。
     * @return 返回结果
     */
    public GreenChannelActivatedEvent(Long triageId, Long patientId, Long encounterId, String triageLevelEnum) {
        super(triageId, AggregateTypeConstants.EMERGENCY_TRIAGE);
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.triageLevelEnum = triageLevelEnum;
    }
}
