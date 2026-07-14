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
 * 服务申请已核对事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、performerCheckId。</p>
 */
@Getter
@Setter
public class ServiceRequestVerifiedEvent extends DomainEvent {

    /** 核对执行人标识。 */
    private Long performerCheckId;

    /**
     * 迁移后的持久化状态码（与写模型 {@code wkf_service_request.status_enum} 一致），供读模型投影使用。
     */
    private String statusEnum;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestVerifiedEvent() {
        super();
    }

    /**
     * 构造服务申请已核对事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param performerCheckId 核对执行人标识
     * @param statusEnum       持久化状态码（须与聚合保存后的状态一致）
     */
    public ServiceRequestVerifiedEvent(Long serviceRequestId, Long performerCheckId, String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.performerCheckId = performerCheckId;
        this.statusEnum = statusEnum;
    }
}
