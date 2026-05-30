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
import java.time.LocalDateTime;

/**
 * 药品已发放事件。
 */
@Getter
@Setter
public class MedicationDispensedEvent extends DomainEvent {
    /** 药品定义标识。 */
    private Long medicationId;
    /** 批号。 */
    private String lotNumber;
    /** 实际发放数量。 */
    private BigDecimal dispenseQuantity;

    /** 操作人/审计人标识（命令下达者）。 */
    private Long operatorId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;
    /** 发药人标识（写模型 practitionerId）。 */
    private Long dispenserId;
    /** 发药时间。 */
    private LocalDateTime dispenseTime;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDispensedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationDispensedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, Long medicationId, String lotNumber, BigDecimal dispenseQuantity逻辑。
     * @return 返回结果
     */
    public MedicationDispensedEvent(Long id, Long medicationId, String lotNumber, BigDecimal dispenseQuantity) {
        this(id, medicationId, lotNumber, dispenseQuantity, null);
    }

    /**
     * 已发药（含操作人/审计人）。
     *
     * @param id 发放单标识
     * @param medicationId 药品定义标识
     * @param lotNumber 批号
     * @param dispenseQuantity 实际发放数量
     * @param operatorId 操作人标识
     */
    public MedicationDispensedEvent(
        Long id,
        Long medicationId,
        String lotNumber,
        BigDecimal dispenseQuantity,
        Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.medicationId = medicationId;
        this.lotNumber = lotNumber;
        this.dispenseQuantity = dispenseQuantity;
        this.operatorId = operatorId;
    }
}
