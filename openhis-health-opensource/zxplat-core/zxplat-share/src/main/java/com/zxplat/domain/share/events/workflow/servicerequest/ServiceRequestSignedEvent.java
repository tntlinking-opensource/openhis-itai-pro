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
 * 服务申请已签发领域事件（DM-WKF-004 §5.1.4）。
 *
 * <p>最小载荷：serviceRequestId（经 aggregateId）、categoryEnum、save 后 statusEnum
 *（通常为 {@code RequestStatus.ACTIVE}），供 OrderInfo 等读模型写入已签发态。</p>
 */
@Getter
@Setter
public class ServiceRequestSignedEvent extends DomainEvent {

    /** 申请类别枚举 code。 */
    private String categoryEnum;

    /** 签发后状态（RequestStatus#getCode()，通常为 ACTIVE）。 */
    private String statusEnum;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestSignedEvent() {
        super();
    }

    /**
     * 构造服务申请已签发事件（兼容旧调用；statusEnum 为空时投影器不覆盖状态列）。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param categoryEnum     申请类别 code
     */
    public ServiceRequestSignedEvent(Long serviceRequestId, String categoryEnum) {
        this(serviceRequestId, categoryEnum, null);
    }

    /**
     * 构造服务申请已签发事件（含签发后状态，供 OrderInfo 读模型投影）。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param categoryEnum     申请类别 code
     * @param statusEnum       签发后状态码（save 后实体 status_enum）
     */
    public ServiceRequestSignedEvent(
        Long serviceRequestId, String categoryEnum, String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.categoryEnum = categoryEnum;
        this.statusEnum = statusEnum;
    }
}
