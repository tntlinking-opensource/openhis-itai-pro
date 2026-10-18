/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 挂号单已打印事件。打印挂号单成功时发布；用于读模型更新打印次数及获取挂号单号。
 */
@Getter
@Setter
public class RegistrationSlipPrintedEvent extends DomainEvent {

    /** 就诊标识（用于定位挂号单读模型）。 */
    private Long encounterId;

    /** 挂号单号（由写模型生成后携带，读模型直接赋值，无需投影器自行拼接）。 */
    private String slipNo;

    /** 无参构造器（反序列化用）。 */
    public RegistrationSlipPrintedEvent() {
        super();
    }

    /**
     * 全参构造器。
     *
     * @param aggregateId 聚合根 ID（通常等于 encounterId）
     * @param encounterId 就诊标识
     * @param slipNo      挂号单号
     */
    public RegistrationSlipPrintedEvent(Long aggregateId, Long encounterId, String slipNo) {
        super(aggregateId, AggregateTypeConstants.ENCOUNTER);
        this.encounterId = encounterId;
        this.slipNo = slipNo;
    }
}
