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
public class LongTermMedicationOrderStoppedEvent extends DomainEvent {
    private LocalDateTime effectiveDoseEnd;

    private Long operatorId;

    /** 停嘱后请求状态（{@link com.openhis.health.domain.share.enums.RequestStatus#getCode()}，如 STOPPED）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LongTermMedicationOrderStoppedEvent() {
        super();
    }

    /**
     * 处理Long id, LocalDateTime effectiveDoseEnd逻辑。
     * @return 返回结果
     */
    public LongTermMedicationOrderStoppedEvent(
        Long id, LocalDateTime effectiveDoseEnd, Long operatorId, String statusEnum) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.effectiveDoseEnd = effectiveDoseEnd;
        this.operatorId = operatorId;
        this.statusEnum = statusEnum;
    }
}
