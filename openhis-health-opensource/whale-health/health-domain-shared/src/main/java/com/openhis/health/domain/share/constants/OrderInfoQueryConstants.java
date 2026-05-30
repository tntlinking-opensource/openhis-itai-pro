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
 * OrderInfo 读模型查询共享常量（读模型 §3 Q-ORD-007、AS-IP-DOR-003 IF-IP-DOR-10）。
 *
 * <p>与 {@link ReadModelOrderTypeConstants} 配合，避免在 {@code *RMQueryService} 内散落 order_type 字面量。</p>
 */
public interface OrderInfoQueryConstants {

    /**
     * 住院医生站医嘱列表允许的 {@code order_type} 条目数（药品 + 诊疗，不含耗材）。
     */
    int INPATIENT_DOCTOR_STATION_ORDER_TYPE_SLOT_COUNT = 2;
}
