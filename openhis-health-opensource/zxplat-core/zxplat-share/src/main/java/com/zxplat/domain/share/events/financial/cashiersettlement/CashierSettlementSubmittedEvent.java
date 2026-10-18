/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.cashiersettlement;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 交账提交成功事件（status=SUBMITTED）。
 */
@Getter
@Setter
public class CashierSettlementSubmittedEvent extends DomainEvent {

    /** 交账单号。 */
    private String busNo;

    /** 交账类型 code。 */
    private String typeEnum;

    /** 提交人。 */
    private Long submitterId;

    /** 交账所属科室。 */
    private Long orgId;

    /** 交账提交时刻。 */
    private LocalDateTime submittedAt;

    /** 本次挂载源行条数。 */
    private Integer paymentCount;

    /** 操作人（审计）。 */
    private Long operatorId;

    /**
     * 无参构造（反序列化用）。
     */
    public CashierSettlementSubmittedEvent() {
        super();
    }

    /**
     * 构造交账提交事件载荷。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param typeEnum     交账类型 code
     * @param submitterId  提交人
     * @param orgId        科室
     * @param submittedAt  提交时刻
     * @param paymentCount 挂载条数
     * @param operatorId   操作人
     */
    public CashierSettlementSubmittedEvent(
        Long settlementId,
        String busNo,
        String typeEnum,
        Long submitterId,
        Long orgId,
        LocalDateTime submittedAt,
        Integer paymentCount,
        Long operatorId) {
        super(settlementId, AggregateTypeConstants.CASHIER_SETTLEMENT);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.submitterId = submitterId;
        this.orgId = orgId;
        this.submittedAt = submittedAt;
        this.paymentCount = paymentCount;
        this.operatorId = operatorId;
    }
}
