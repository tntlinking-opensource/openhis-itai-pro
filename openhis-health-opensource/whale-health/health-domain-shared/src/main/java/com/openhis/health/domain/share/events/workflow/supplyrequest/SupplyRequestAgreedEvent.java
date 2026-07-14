/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
