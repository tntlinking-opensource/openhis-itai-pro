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
 * MedicationDispensePendingRefundCreatedEvent：health-domain-shared 跨领域共享模型。
 */
public class MedicationDispensePendingRefundCreatedEvent extends DomainEvent {
    /** origin发药主键（MedicationDispensePendingRefundCreated 业务上下文）。 */
    private Long originDispenseId;
    private BigDecimal refundQuantity;

    /** 操作人主键（MedicationDispensePendingRefundCreatedEvent）。 */
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
