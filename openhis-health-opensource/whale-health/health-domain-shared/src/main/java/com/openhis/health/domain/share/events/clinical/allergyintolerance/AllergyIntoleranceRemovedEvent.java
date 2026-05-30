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

package com.openhis.health.domain.share.events.clinical.allergyintolerance;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 过敏记录已删除事件。
 */
@Getter
@Setter
public class AllergyIntoleranceRemovedEvent extends DomainEvent {
    private Long encounterId;
    private Long patientId;

    /**
     * 构造事件。
     */
    public AllergyIntoleranceRemovedEvent(Long id, Long encounterId, Long patientId) {
        super(id, AggregateTypeConstants.ALLERGY_INTOLERANCE);
        this.encounterId = encounterId;
        this.patientId = patientId;
    }
}
