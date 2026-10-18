/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.devicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 耗材草稿已删除事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、encounterId。</p>
 */
@Getter
@Setter
public class DeviceOrderDraftRemovedEvent extends DomainEvent {

    /** 关联就诊标识。 */
    private Long encounterId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderDraftRemovedEvent() {
        super();
    }

    /**
     * 构造耗材草稿已删除事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param encounterId     关联就诊标识
     */
    public DeviceOrderDraftRemovedEvent(Long deviceRequestId, Long encounterId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.encounterId = encounterId;
    }
}
