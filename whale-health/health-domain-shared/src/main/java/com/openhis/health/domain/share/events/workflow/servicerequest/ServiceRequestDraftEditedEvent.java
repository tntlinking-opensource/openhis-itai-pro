/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.workflow.servicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请草稿已编辑事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、changedFields（动态变更摘要）。</p>
 */
@Getter
@Setter
public class ServiceRequestDraftEditedEvent extends DomainEvent {

    /** 变更字段名称列表（逗号分隔，动态计算，禁止硬编码）。 */
    private String changedFields;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestDraftEditedEvent() {
        super();
    }

    /**
     * 构造服务申请草稿已编辑事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param changedFields    变更字段名称（逗号分隔，动态计算）
     */
    public ServiceRequestDraftEditedEvent(Long serviceRequestId, String changedFields) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.changedFields = changedFields;
    }
}
