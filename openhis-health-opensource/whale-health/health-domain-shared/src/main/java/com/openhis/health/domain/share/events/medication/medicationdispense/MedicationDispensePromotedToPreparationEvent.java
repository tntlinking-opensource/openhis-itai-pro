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

package com.openhis.health.domain.share.events.medication.medicationdispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品发放单由草稿推进为待配药事件（门诊收费等链路）。
 */
@Getter
@Setter
public class MedicationDispensePromotedToPreparationEvent extends DomainEvent {

    private Long medReqId;

    private Long operatorId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;

    public MedicationDispensePromotedToPreparationEvent() {
        super();
    }

    public MedicationDispensePromotedToPreparationEvent(Long dispenseId, Long medReqId, Long operatorId, String statusEnumCode) {
        super(dispenseId, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.medReqId = medReqId;
        this.operatorId = operatorId;
        this.statusEnumCode = statusEnumCode;
    }
}
