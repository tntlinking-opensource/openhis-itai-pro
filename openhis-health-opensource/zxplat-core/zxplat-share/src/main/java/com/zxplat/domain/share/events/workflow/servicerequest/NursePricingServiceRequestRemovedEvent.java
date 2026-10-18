/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.servicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 护士划价诊疗服务申请已逻辑删除事件。
 *
 * <p>与 {@link NursePricingServiceRequestCreatedEvent} 对称；供已划价列表等读侧剔除投影（AS-IP-NUR-005 IF-IP-PRC-06）。</p>
 */
@Getter
@Setter
public class NursePricingServiceRequestRemovedEvent extends DomainEvent {

    /** 关联就诊标识。 */
    private Long encounterId;

    /** 上级医嘱来源表。 */
    private String basedOnTable;

    /** 上级医嘱来源 ID。 */
    private Long basedOnId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public NursePricingServiceRequestRemovedEvent() {
        super();
    }

    /**
     * 构造护士划价诊疗服务申请已删除事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param encounterId      关联就诊标识
     * @param basedOnTable     上级医嘱来源表
     * @param basedOnId        上级医嘱来源 ID
     */
    public NursePricingServiceRequestRemovedEvent(
        Long serviceRequestId,
        Long encounterId,
        String basedOnTable,
        Long basedOnId) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.encounterId = encounterId;
        this.basedOnTable = basedOnTable;
        this.basedOnId = basedOnId;
    }
}
