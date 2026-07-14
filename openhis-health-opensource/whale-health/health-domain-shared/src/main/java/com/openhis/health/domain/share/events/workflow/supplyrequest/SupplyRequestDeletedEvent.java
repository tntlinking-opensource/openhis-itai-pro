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
 * SupplyRequestDeletedEvent 领域事件。
 */
@Getter
@Setter
public class SupplyRequestDeletedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 单据类型。 */
    private String typeEnum;

    /** 操作人标识。 */
    private Long operatorId;

    /** 删除前末态状态编码（可选，供读模型投影）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplyRequestDeletedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, String typeEnum, Long operatorId逻辑。
     * @return 返回结果
     */
    public SupplyRequestDeletedEvent(Long aggregateId, String busNo, String typeEnum, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.operatorId = operatorId;
    }
}
