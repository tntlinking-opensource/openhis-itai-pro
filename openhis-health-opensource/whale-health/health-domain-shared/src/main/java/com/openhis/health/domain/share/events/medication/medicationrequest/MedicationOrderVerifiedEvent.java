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

package com.openhis.health.domain.share.events.medication.medicationrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MedicationOrderVerifiedEvent extends DomainEvent {
    private Long performerCheckId;
    private LocalDateTime checkTime;
    /** 持久化状态码（RequestStatus#getCode()，save 后取自写模型）。 */
    private String statusEnum;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderVerifiedEvent() {
        super();
    }

    /**
     * 处理Long id, Long performerCheckId, LocalDateTime checkTime逻辑。
     * @return 返回结果
     */
    public MedicationOrderVerifiedEvent(Long id, Long performerCheckId, LocalDateTime checkTime, Long operatorId, String statusEnum) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.performerCheckId = performerCheckId;
        this.checkTime = checkTime;
        this.operatorId = operatorId;
        this.statusEnum = statusEnum;
    }
}
