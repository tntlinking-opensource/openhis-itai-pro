/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.priceadjustment;

/**
 * 调价单审批通过领域事件（DM-CUS-011 §5）。
 *
 * <p>触发于库存解锁与主行状态迁移为 approved 之后；定价写价由应用层同事务编排。</p>
 */
public class PriceAdjustmentApprovedEvent extends PriceAdjustmentSnapshotEvent {

    /**
     * 反序列化/框架用默认构造。
     */
    public PriceAdjustmentApprovedEvent() {
        super();
    }

    /**
     * 按锚点主键构造。
     *
     * @param aggregateId 调价单首行主键
     */
    public PriceAdjustmentApprovedEvent(Long aggregateId) {
        super(aggregateId);
    }
}
