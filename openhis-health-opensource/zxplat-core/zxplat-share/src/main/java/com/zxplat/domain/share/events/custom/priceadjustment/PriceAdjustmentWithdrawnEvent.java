/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.priceadjustment;

/**
 * 调价草稿撤销领域事件（DM-CUS-011 §5）。
 *
 * <p>触发于 draft → deprecated；不涉及库存解锁。</p>
 */
public class PriceAdjustmentWithdrawnEvent extends PriceAdjustmentSnapshotEvent {

    /**
     * 反序列化/框架用默认构造。
     */
    public PriceAdjustmentWithdrawnEvent() {
        super();
    }

    /**
     * 按锚点主键构造。
     *
     * @param aggregateId 调价单首行主键
     */
    public PriceAdjustmentWithdrawnEvent(Long aggregateId) {
        super(aggregateId);
    }
}
