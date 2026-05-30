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

package com.openhis.health.domain.share.events.workflow.supplyrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SupplyRequestRejectedEvent 领域事件。
 */
@Getter
@Setter
public class SupplyRequestRejectedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 单据类型。 */
    private String typeEnum;

    /** 审批人标识。 */
    private Long approverId;

    /** 审批时间。 */
    private java.time.LocalDateTime approvalTime;

    /** 驳回原因码（可选）。 */
    private String changeReasonCode;

    /** 操作人标识。 */
    private Long operatorId;

    /** 驳回后状态编码（供读模型投影）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplyRequestRejectedEvent() {
        super();
    }

    public SupplyRequestRejectedEvent(
        Long aggregateId,
        String busNo,
        String typeEnum,
        Long approverId,
        java.time.LocalDateTime approvalTime,
        String changeReasonCode,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.approverId = approverId;
        this.approvalTime = approvalTime;
        this.changeReasonCode = changeReasonCode;
        this.operatorId = operatorId;
    }
}
