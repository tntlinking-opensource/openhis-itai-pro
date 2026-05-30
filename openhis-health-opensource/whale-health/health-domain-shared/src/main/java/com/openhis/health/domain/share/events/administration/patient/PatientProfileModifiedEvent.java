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

import java.util.List;

/**
 * 患者档案已修改事件。
 */
@Getter
@Setter
public class PatientProfileModifiedEvent extends DomainEvent {

    /**
     * 变更字段列表。
     */
    private List<String> changedFields;

    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 无参构造函数。
     */
    public PatientProfileModifiedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param changedFields 变更字段列表
     * @param operatorId 操作人标识
     */
    public PatientProfileModifiedEvent(Long patientId, List<String> changedFields, Long operatorId) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.changedFields = changedFields;
        this.operatorId = operatorId;
    }
}
