/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationdispense;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * MedicationDispenseSummaryCancelledEvent：health-domain-shared 跨领域共享模型。
 */
public class MedicationDispenseSummaryCancelledEvent extends DomainEvent {
    /** 汇总号（MedicationDispenseSummaryCancelled 业务上下文）。 */
    private String summaryNo;

    /** 操作人主键（MedicationDispenseSummaryCancelledEvent）。 */
    private Long operatorId;

    /** 取消汇总后发放单持久化状态码（与写模型一致）。 */
    private String slipStatusEnumCode;

    /**
     * 汇总发药单头持久化状态码（与 {@link com.zxplat.domain.share.enums.MedicationSummaryStatus#getCode()} 一致），供读模型投影。
     */
    private String summaryHeaderStatusEnumCode;

    /** 反序列化用无参构造。 */
    public MedicationDispenseSummaryCancelledEvent() {
        super();
    }

    /**
     * 取消汇总：指定发放单明细解绑汇总单。
     *
     * @param id 发放单主键（聚合锚点）
     * @param summaryNo 原汇总单号
     * @param operatorId 操作人
     */
    public MedicationDispenseSummaryCancelledEvent(Long id, String summaryNo, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.summaryNo = summaryNo;
        this.operatorId = operatorId;
    }
}
