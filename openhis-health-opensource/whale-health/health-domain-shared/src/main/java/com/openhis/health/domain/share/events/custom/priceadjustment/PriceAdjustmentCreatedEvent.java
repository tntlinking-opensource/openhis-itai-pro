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
 * 价格调整草稿已创建事件。
 */
public class PriceAdjustmentCreatedEvent extends PriceAdjustmentSnapshotEvent {
    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public PriceAdjustmentCreatedEvent() {
        super();
    }

    /**
     * 处理Long adjustmentId逻辑。
     * @return 返回结果
     */
    public PriceAdjustmentCreatedEvent(Long adjustmentId) {
        super(adjustmentId);
    }
}

