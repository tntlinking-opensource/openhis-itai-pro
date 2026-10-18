/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.priceadjustment;

/**
 * 调价单已提交审批领域事件（DM-CUS-011 §5）。
 *
 * <p>触发于互斥通过、子表快照、全院加锁与状态迁移为 pending 之后；载荷含 {@code lockedInventoryCount}。</p>
 */
public class PriceAdjustmentSubmittedEvent extends PriceAdjustmentSnapshotEvent {

    /**
     * 反序列化/框架用默认构造。
     */
    public PriceAdjustmentSubmittedEvent() {
        super();
    }

    /**
     * 按锚点主键构造。
     *
     * @param aggregateId 调价单首行主键
     */
    public PriceAdjustmentSubmittedEvent(Long aggregateId) {
        super(aggregateId);
    }
}
