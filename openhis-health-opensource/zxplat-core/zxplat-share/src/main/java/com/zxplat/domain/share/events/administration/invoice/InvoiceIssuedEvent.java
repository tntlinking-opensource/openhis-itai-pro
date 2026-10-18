/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.invoice;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 票据已开具事件（电子票据成功生成后通知下游）。
 *
 * <p>SDD-ADM-012 §7 最小载荷：invoiceId（via super.aggregateId）、patientId、reconciliationId、
 * billNo、billBatchCode、billCreateTime、typeCode。</p>
 */
@Getter
@Setter
public class InvoiceIssuedEvent extends DomainEvent {

    /** 患者标识（下游通知患者用）。 */
    private Long patientId;

    /** 关联结算单标识。 */
    private Long reconciliationId;

    /** 电子票据编号。 */
    private String billNo;

    /** 电子票据批次码。 */
    private String billBatchCode;

    /** 票据开具时间。 */
    private LocalDateTime billCreateTime;

    /** 票据类型代码。 */
    private String typeCode;
    /** 收费员标识。 */
    private Long entererId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public InvoiceIssuedEvent() {
        super();
    }

    /**
     * 构造票据开具事件。
     *
     * @param invoiceId       票据主键（aggregateId）
     * @param patientId       患者标识
     * @param reconciliationId 关联结算单标识
     * @param billNo          电子票据编号
     * @param billBatchCode   电子票据批次码
     * @param billCreateTime  票据开具时间
     * @param typeCode        票据类型代码
     */
    public InvoiceIssuedEvent(Long invoiceId, Long patientId, Long reconciliationId,
                              String billNo, String billBatchCode,
                              LocalDateTime billCreateTime, String typeCode) {
        super(invoiceId, AggregateTypeConstants.INVOICE);
        this.patientId = patientId;
        this.reconciliationId = reconciliationId;
        this.billNo = billNo;
        this.billBatchCode = billBatchCode;
        this.billCreateTime = billCreateTime;
        this.typeCode = typeCode;
    }
}
