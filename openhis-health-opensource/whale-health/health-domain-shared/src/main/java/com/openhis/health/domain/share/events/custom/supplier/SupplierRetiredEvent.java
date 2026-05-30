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

package com.openhis.health.domain.share.events.custom.supplier;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应商已废弃事件。
 */
@Getter
@Setter
public class SupplierRetiredEvent extends DomainEvent {
    /**
     * 供应商业务编码。
     */
    private String busNo;
    /**
     * 废弃原因（可空）。
     */
    private String reason;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplierRetiredEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, String reason逻辑。
     * @return 返回结果
     */
    public SupplierRetiredEvent(Long aggregateId, String busNo, String reason, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLIER);
        this.busNo = busNo;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
