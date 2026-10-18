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
 * 护理医嘱已维护事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、categoryEnum。</p>
 */
@Getter
@Setter
public class NursingOrderUpsertedEvent extends DomainEvent {

    /** 护理类别枚举。 */
    private String categoryEnum;

    /**
     * 默认构造函数（反序列化用）。
     */
    public NursingOrderUpsertedEvent() {
        super();
    }

    /**
     * 构造护理医嘱已维护事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param categoryEnum     护理类别枚举
     */
    public NursingOrderUpsertedEvent(Long serviceRequestId, String categoryEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.categoryEnum = categoryEnum;
    }
}
