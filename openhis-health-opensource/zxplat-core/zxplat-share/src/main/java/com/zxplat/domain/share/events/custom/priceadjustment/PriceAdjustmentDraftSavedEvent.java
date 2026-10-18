/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.priceadjustment;

/**
 * 调价草稿已保存领域事件（DM-CUS-011 §5）。
 *
 * <p>触发于主行全量对齐与子表预展开之后；草稿阶段不加锁。</p>
 */
public class PriceAdjustmentDraftSavedEvent extends PriceAdjustmentSnapshotEvent {

    /**
     * 反序列化/框架用默认构造。
     */
    public PriceAdjustmentDraftSavedEvent() {
        super();
    }

    /**
     * 按锚点主键构造。
     *
     * @param aggregateId 调价单首行主键
     */
    public PriceAdjustmentDraftSavedEvent(Long aggregateId) {
        super(aggregateId);
    }
}
