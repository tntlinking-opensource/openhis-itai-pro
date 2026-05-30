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

package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订货单分页列表请求。分页与关键字见 {@link AbstractPageQuery}（B.10.11）。
 */
@Getter
@Setter
public class PagePurchaseOrdersRequest extends AbstractHealthPageRequest {

    /**
     * 列表筛选：订货目的药库（必填），对应读模型 {@code purpose_location_id}；
     * {@code 0} 表示当前机构下全部药库（与初始化响应 {@code allWarehousesOption} 对齐）。
     */
    private Long purposeLocationId;
    /** 状态筛选（SupplyStatus 序列号，可选）。 */
    private Integer statusEnum;
    /** 申请时间起。 */
    private LocalDateTime applyTimeFrom;
    /** 申请时间止。 */
    private LocalDateTime applyTimeTo;
    /** 单据类型；固定为 1（药库订货），传入其它值将被拒绝。 */
    private Integer typeEnum;
}
