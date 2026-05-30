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
 * 住院结算工作台队列页签常量（AS-IP-FIN-003 IF-IP-STL-02）。
 */
public interface InpatientSettleQueueTab {

    /** 有待结算费用（存在 {@code PENDING_SETTLEMENT} 收费行）。 */
    String PENDING_SETTLE = "PENDING_SETTLE";

    /** 已财务结算（就诊 {@code COMPLETED}）。 */
    String SETTLED = "SETTLED";
}
