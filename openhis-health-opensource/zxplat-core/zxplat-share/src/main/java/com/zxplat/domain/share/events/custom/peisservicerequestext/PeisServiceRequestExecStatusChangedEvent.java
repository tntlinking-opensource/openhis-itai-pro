/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.peisservicerequestext;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检项目执行状态变更事件（DM-PEIS-001 updateExecStatus 后发布）。
 */
@Getter
@Setter
public class PeisServiceRequestExecStatusChangedEvent extends DomainEvent {

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 执行状态 code。 */
    private String execStatusEnum;

    /**
     * 构造执行状态变更事件。
     *
     * @param serviceRequestId 服务申请主键
     * @param encounterId      就诊主键
     * @param execStatusEnum   执行状态
     */
    public PeisServiceRequestExecStatusChangedEvent(
        Long serviceRequestId, Long encounterId, String execStatusEnum) {
        super(serviceRequestId, AggregateTypeConstants.PEIS_SERVICE_REQUEST_EXT);
        this.serviceRequestId = serviceRequestId;
        this.encounterId = encounterId;
        this.execStatusEnum = execStatusEnum;
    }
}
