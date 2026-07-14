/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.priceadjustment;

/**
 * 调价单审批驳回领域事件（DM-CUS-011 §5）。
 *
 * <p>触发于库存解锁与主行状态迁移为 rejected 之后。</p>
 */
public class PriceAdjustmentRejectedEvent extends PriceAdjustmentSnapshotEvent {

    /**
     * 反序列化/框架用默认构造。
     */
    public PriceAdjustmentRejectedEvent() {
        super();
    }

    /**
     * 按锚点主键构造。
     *
     * @param aggregateId 调价单首行主键
     */
    public PriceAdjustmentRejectedEvent(Long aggregateId) {
        super(aggregateId);
    }
}
