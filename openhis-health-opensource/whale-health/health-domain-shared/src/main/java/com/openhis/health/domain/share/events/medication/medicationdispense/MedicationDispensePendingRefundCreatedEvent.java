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

import java.math.BigDecimal;

@Getter
@Setter
public class MedicationDispensePendingRefundCreatedEvent extends DomainEvent {
    private Long originDispenseId;
    private BigDecimal refundQuantity;

    private Long operatorId;

    /** 执行记录标识（住院取消执行挂接新 CANCEL 行；门诊退药申请可为空）。 */
    private Long procedureId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDispensePendingRefundCreatedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationDispensePendingRefundCreatedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, Long originDispenseId, BigDecimal refundQuantity逻辑。
     * @return 返回结果
     */
    public MedicationDispensePendingRefundCreatedEvent(Long id, Long originDispenseId, BigDecimal refundQuantity) {
        this(id, originDispenseId, refundQuantity, null);
    }

    public MedicationDispensePendingRefundCreatedEvent(
        Long id, Long originDispenseId, BigDecimal refundQuantity, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.originDispenseId = originDispenseId;
        this.refundQuantity = refundQuantity;
        this.operatorId = operatorId;
    }
}
