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
 * 服务申请草稿已创建事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、encounterId、patientId。</p>
 */
@Getter
@Setter
public class ServiceRequestDraftCreatedEvent extends DomainEvent {

    /** 关联就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestDraftCreatedEvent() {
        super();
    }

    /**
     * 构造服务申请草稿已创建事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param encounterId      关联就诊标识
     * @param patientId        患者标识
     */
    public ServiceRequestDraftCreatedEvent(Long serviceRequestId, Long encounterId, Long patientId) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.encounterId = encounterId;
        this.patientId = patientId;
    }
}
