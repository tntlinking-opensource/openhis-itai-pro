/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.invoice;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
/**
 * InvoiceDraftCreatedEvent。
 */
public class InvoiceDraftCreatedEvent extends DomainEvent {
    /**
     * patientId。
     */
    private Long patientId;
    /**
     * paymentId。
     */
    private Long paymentId;
    /**
     * typeCode。
     */
    private String typeCode;
    /**
     * amount。
     */
    private BigDecimal amount;

    public InvoiceDraftCreatedEvent() { super(); }
    /**
     * 处理Long id, Long patientId, Long paymentId, String typeCode, BigDecimal amount逻辑。
     * @return 返回结果
     */
    public InvoiceDraftCreatedEvent(Long id, Long patientId, Long paymentId, String typeCode, BigDecimal amount) {
        super(id, AggregateTypeConstants.INVOICE);
        this.patientId = patientId;
        this.paymentId = paymentId;
        this.typeCode = typeCode;
        this.amount = amount;
    }
}
