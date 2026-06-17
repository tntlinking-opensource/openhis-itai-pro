/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.events.insurance;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 协同单创建事件（Worker afterCommit 可选加速派发）。
 */
@Getter
@Setter
public class YbCoordinationCreatedEvent extends DomainEvent {

    /** 关联支付单主键。 */
    private Long paymentId;
    /** 关联就诊主键。 */
    private Long encounterId;
    /** 协同单业务类型 code。 */
    private String bizType;

    public YbCoordinationCreatedEvent() {
        super();
    }

    /**
     * 构造协同单创建事件。
     *
     * @param coordinationId 协同单主键
     * @param paymentId      支付单主键
     * @param encounterId    就诊主键
     * @param bizType        业务类型 code
     */
    public YbCoordinationCreatedEvent(Long coordinationId, Long paymentId, Long encounterId, String bizType) {
        super(coordinationId, AggregateTypeConstants.YB_COORDINATION);
        this.paymentId = paymentId;
        this.encounterId = encounterId;
        this.bizType = bizType;
    }
}
