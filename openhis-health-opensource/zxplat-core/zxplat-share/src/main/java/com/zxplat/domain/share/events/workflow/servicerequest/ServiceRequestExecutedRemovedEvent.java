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
 * 执行派生服务申请已逻辑删除事件。
 *
 * <p>与 {@link ServiceRequestExecutedCreatedEvent} 对称；最小载荷：serviceRequestId、basedOnTable、basedOnId。</p>
 */
@Getter
@Setter
public class ServiceRequestExecutedRemovedEvent extends DomainEvent {

    /** 来源表名。 */
    private String basedOnTable;

    /** 来源记录主键。 */
    private Long basedOnId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestExecutedRemovedEvent() {
        super();
    }

    /**
     * 构造执行派生服务申请已删除事件。
     *
     * @param serviceRequestId 服务申请主键
     * @param basedOnTable     来源表名
     * @param basedOnId        来源记录主键
     */
    public ServiceRequestExecutedRemovedEvent(Long serviceRequestId, String basedOnTable, Long basedOnId) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.basedOnTable = basedOnTable;
        this.basedOnId = basedOnId;
    }
}
