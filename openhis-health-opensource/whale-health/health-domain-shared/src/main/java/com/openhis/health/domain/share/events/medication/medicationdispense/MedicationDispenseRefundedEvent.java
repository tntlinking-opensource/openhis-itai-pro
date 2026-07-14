/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationdispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter

/**
 * MedicationDispenseRefundedEvent：health-domain-shared 跨领域共享模型。
 */
public class MedicationDispenseRefundedEvent extends DomainEvent {
    /** 退药数量。 */
    private BigDecimal refundQuantity;

    /** 操作人主键（MedicationDispenseRefundedEvent）。 */
    private Long operatorId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDispenseRefundedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationDispenseRefundedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, BigDecimal refundQuantity逻辑。
     * @return 返回结果
     */
    public MedicationDispenseRefundedEvent(Long id, BigDecimal refundQuantity) {
        this(id, refundQuantity, null);
    }

    public MedicationDispenseRefundedEvent(Long id, BigDecimal refundQuantity, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.refundQuantity = refundQuantity;
        this.operatorId = operatorId;
    }
}
