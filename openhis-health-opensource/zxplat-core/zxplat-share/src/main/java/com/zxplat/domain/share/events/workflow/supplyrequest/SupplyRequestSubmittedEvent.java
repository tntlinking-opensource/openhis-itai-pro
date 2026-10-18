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
 * SupplyRequestSubmittedEvent 领域事件。
 */
@Getter
@Setter
public class SupplyRequestSubmittedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 单据类型。 */
    private String typeEnum;

    /** 申请时间。 */
    private java.time.LocalDateTime applyTime;

    /** 申请人标识。 */
    private Long applicantId;

    /** 操作人标识。 */
    private Long operatorId;

    /** 提交后单据状态编码（与 {@code wkf_supply_request.status_enum} 一致，供读模型投影）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplyRequestSubmittedEvent() {
        super();
    }

    public SupplyRequestSubmittedEvent(
        Long aggregateId,
        String busNo,
        String typeEnum,
        java.time.LocalDateTime applyTime,
        Long applicantId,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.applyTime = applyTime;
        this.applicantId = applicantId;
        this.operatorId = operatorId;
    }
}
