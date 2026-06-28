/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.custom.priceadjustment;

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
