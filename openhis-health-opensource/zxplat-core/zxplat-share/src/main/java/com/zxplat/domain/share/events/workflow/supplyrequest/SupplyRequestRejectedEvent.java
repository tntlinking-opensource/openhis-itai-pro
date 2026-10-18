/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.supplyrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
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
