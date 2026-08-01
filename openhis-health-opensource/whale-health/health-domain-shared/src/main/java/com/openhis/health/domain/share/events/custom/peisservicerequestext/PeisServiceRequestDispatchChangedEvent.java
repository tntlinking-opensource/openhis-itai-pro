/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.peisservicerequestext;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检项目外发标志变更事件（DM-PEIS-001 updateDispatchFlags 后发布）。
 */
@Getter
@Setter
public class PeisServiceRequestDispatchChangedEvent extends DomainEvent {

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 模块（LIS / PACS）。 */
    private String moduleEnum;

    /** 外发状态 code。 */
    private String dispatchEnum;

    /**
     * 构造外发标志变更事件。
     *
     * @param serviceRequestId 服务申请主键
     * @param moduleEnum       模块
     * @param dispatchEnum     外发状态
     */
    public PeisServiceRequestDispatchChangedEvent(
        Long serviceRequestId, String moduleEnum, String dispatchEnum) {
        super(serviceRequestId, AggregateTypeConstants.PEIS_SERVICE_REQUEST_EXT);
        this.serviceRequestId = serviceRequestId;
        this.moduleEnum = moduleEnum;
        this.dispatchEnum = dispatchEnum;
    }
}
