/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.invoice;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 票据已取消事件（冲红或作废草稿场景均通过本事件通知下游）。
 *
 * <p>SDD-ADM-012 §7 最小载荷：invoiceId（via super.aggregateId）、cancelledReason、operatorId、typeCode。</p>
 */
@Getter
@Setter
public class InvoiceCancelledEvent extends DomainEvent {

    /** 取消原因。 */
    private String cancelledReason;

    /** 操作人标识。 */
    private Long operatorId;

    /** 票据类型代码（区分电子票据类型）。 */
    private String typeCode;
    /** 收费员标识。 */
    private Long entererId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public InvoiceCancelledEvent() {
        super();
    }

    /**
     * 构造票据取消事件。
     *
     * @param invoiceId       票据主键（aggregateId）
     * @param cancelledReason 取消原因
     * @param operatorId      操作人标识
     * @param typeCode        票据类型代码
     */
    public InvoiceCancelledEvent(Long invoiceId, String cancelledReason, Long operatorId, String typeCode) {
        super(invoiceId, AggregateTypeConstants.INVOICE);
        this.cancelledReason = cancelledReason;
        this.operatorId = operatorId;
        this.typeCode = typeCode;
    }
}
