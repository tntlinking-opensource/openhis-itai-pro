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

package com.openhis.health.domain.share.constants;

/**
 * 门诊退费队列页签常量（IF-REF-02；对齐 AS BR-REF-002）。
 */
public interface OutpatientRefundQueueTab {

    /** 全部队列。 */
    String ALL = "ALL";

    /** 待退费标签（就诊仍存在 {@code REFUNDING} 拟退收费明细）。 */
    String PENDING = "PENDING";

    /**
     * 已退费标签（拟退明细已执行完毕，仅余 {@code REFUNDED} 过程终态；对外展示「已退费」）。
     *
     * <p>持久化码仍为 {@code SETTLED}，与门诊收费队列页签字面量复用，语义由 AS-OP-FIN-003 BR-REF-002 限定。</p>
     */
    String SETTLED = "SETTLED";
}
