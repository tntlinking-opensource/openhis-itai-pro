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
 * 药品发放作废事件。
 */
@Getter
@Setter
public class MedicationDispenseDeclinedEvent extends DomainEvent {
    /** 未执行原因枚举编码。 */
    private String notPerformedReasonEnum;

    private Long operatorId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDispenseDeclinedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationDispenseDeclinedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, Integer notPerformedReasonEnum逻辑。
     * @return 返回结果
     */
    public MedicationDispenseDeclinedEvent(Long id, String notPerformedReasonEnum) {
        this(id, notPerformedReasonEnum, null);
    }

    public MedicationDispenseDeclinedEvent(Long id, String notPerformedReasonEnum, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.notPerformedReasonEnum = notPerformedReasonEnum;
        this.operatorId = operatorId;
    }
}

