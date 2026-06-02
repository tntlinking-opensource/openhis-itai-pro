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

package com.openhis.health.domain.share.events.administration.patient;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者非身份证标识同步事件。
 */
@Getter
@Setter
public class PatientIdentifiersSyncedEvent extends DomainEvent {

    /**
     * 同步后的标识数量。
     */
    private Integer identifierCount;

    /**
     * 无参构造函数。
     */
    public PatientIdentifiersSyncedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param identifierCount 同步后的标识数量
     */
    public PatientIdentifiersSyncedEvent(Long patientId, Integer identifierCount) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.identifierCount = identifierCount;
    }
}
