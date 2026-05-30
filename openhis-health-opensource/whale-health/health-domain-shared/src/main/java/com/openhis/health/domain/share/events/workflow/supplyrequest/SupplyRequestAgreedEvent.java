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
 * SupplyRequestAgreedEvent 领域事件。
 */
@Getter
@Setter
public class SupplyRequestAgreedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 单据类型。 */
    private String typeEnum;

    /** 审批人标识。 */
    private Long approverId;

    /** 审批时间。 */
    private java.time.LocalDateTime approvalTime;

    /** 来源单据号（执行单场景可选）。 */
    private String originalBusNo;

    /** 汇总编号（药品汇总单可选）。 */
    private Long summaryId;

    /** 操作人标识。 */
    private Long operatorId;

    /** 同意后状态编码（供读模型投影）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplyRequestAgreedEvent() {
        super();
    }

    public SupplyRequestAgreedEvent(
        Long aggregateId,
        String busNo,
        String typeEnum,
        Long approverId,
        java.time.LocalDateTime approvalTime,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.approverId = approverId;
        this.approvalTime = approvalTime;
        this.operatorId = operatorId;
    }
}
