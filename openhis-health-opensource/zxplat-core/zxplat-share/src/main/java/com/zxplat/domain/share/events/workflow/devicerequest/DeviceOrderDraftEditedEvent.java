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
 * 耗材草稿已编辑事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、changedFields（动态变更摘要）。</p>
 */
@Getter
@Setter
public class DeviceOrderDraftEditedEvent extends DomainEvent {

    /** 变更字段名称列表（逗号分隔，动态计算，禁止硬编码）。 */
    private String changedFields;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderDraftEditedEvent() {
        super();
    }

    /**
     * 构造耗材草稿已编辑事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param changedFields   变更字段名称（逗号分隔，动态计算）
     */
    public DeviceOrderDraftEditedEvent(Long deviceRequestId, String changedFields) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.changedFields = changedFields;
    }
}
