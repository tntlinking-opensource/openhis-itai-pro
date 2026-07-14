/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.servicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 已执行服务申请已创建事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、basedOnTable、basedOnId。</p>
 */
@Getter
@Setter
public class ServiceRequestExecutedCreatedEvent extends DomainEvent {

    /** 来源表名（如 wkf_device_request、wkf_medication_request 等）。 */
    private String basedOnTable;

    /** 来源记录主键。 */
    private Long basedOnId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestExecutedCreatedEvent() {
        super();
    }

    /**
     * 构造已执行服务申请已创建事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param basedOnTable     来源表名
     * @param basedOnId        来源记录主键
     */
    public ServiceRequestExecutedCreatedEvent(Long serviceRequestId, String basedOnTable, Long basedOnId) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.basedOnTable = basedOnTable;
        this.basedOnId = basedOnId;
    }
}
