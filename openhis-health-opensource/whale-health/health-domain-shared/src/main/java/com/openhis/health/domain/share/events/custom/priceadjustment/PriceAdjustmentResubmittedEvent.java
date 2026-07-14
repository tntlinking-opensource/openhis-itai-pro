/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.priceadjustment;

/**
 * 调价单驳回后再次提交领域事件（DM-CUS-011 §5）。
 *
 * <p>语义同 {@link PriceAdjustmentSubmittedEvent}；载荷含 {@code lockedInventoryCount}。</p>
 */
public class PriceAdjustmentResubmittedEvent extends PriceAdjustmentSnapshotEvent {

    /**
     * 反序列化/框架用默认构造。
     */
    public PriceAdjustmentResubmittedEvent() {
        super();
    }

    /**
     * 按锚点主键构造。
     *
     * @param aggregateId 调价单首行主键
     */
    public PriceAdjustmentResubmittedEvent(Long aggregateId) {
        super(aggregateId);
    }
}
