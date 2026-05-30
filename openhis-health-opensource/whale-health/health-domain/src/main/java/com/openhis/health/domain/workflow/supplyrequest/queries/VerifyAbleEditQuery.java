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

package com.openhis.health.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 判断指定业务单号下、某物品行是否仍允许编辑（与 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService#verifyAbleEdit} 配套）。
 *
 * <p>实现侧按 {@code bus_no} 加载全部行后，定位 {@code item_id} 匹配行，并检查其 {@code status_enum} 是否为审核中/已同意等阻塞态（持久化 code，B.10.8.1）。</p>
 */
@Getter
@Setter
public class VerifyAbleEditQuery {

    /** 业务单号。 */
    private String busNo;
    /** 物品定义主键（与行上 {@code item_id} 对齐）。 */
    private Long itemId;
}
